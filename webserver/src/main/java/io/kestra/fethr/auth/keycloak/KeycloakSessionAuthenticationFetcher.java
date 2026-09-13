package io.kestra.fethr.auth.keycloak;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.keycloak.representations.AccessTokenResponse;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.AuthenticationFetcher;
import io.micronaut.security.session.SessionAuthenticationFetcher;
import io.micronaut.session.Session;
import io.micronaut.session.SessionStore;
import io.micronaut.session.http.SessionForRequest;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Resolves the authenticated user from the server-side session (replacing the framework's
 * {@link SessionAuthenticationFetcher}) and keeps it bound to the Keycloak session. When the stored realm
 * access token has expired it refreshes against Keycloak; on success it rebuilds the Authentication from the
 * session, on {@code invalid_grant} (password change, admin logout, or the realm SSO max) it deletes the
 * session and returns NO authentication.
 *
 * <p>
 * Doing this in the authentication step (not a SecurityRule) is what makes a dead session cookie behave
 * correctly: the request becomes unauthenticated, so the intercept-url-map rule rejects protected endpoints
 * (401) while anonymous endpoints stay anonymous. A SecurityRule could not achieve this because it only
 * decides allow/deny on an already-resolved Authentication and cannot clear it.
 *
 * <p>
 * Active only with Micronaut Security on. The HL7 service authenticates with a Keycloak bearer token,
 * resolved by the framework's JWT fetcher.
 */
@Singleton
@Replaces(SessionAuthenticationFetcher.class)
@Requires(property = "micronaut.security.enabled", value = "true")
@Slf4j
public class KeycloakSessionAuthenticationFetcher implements AuthenticationFetcher<HttpRequest<?>> {
    private static final long EXPIRY_SKEW_SECONDS = 10;

    private final KeycloakService keycloakService;
    private final SessionStore<Session> sessionStore;

    public KeycloakSessionAuthenticationFetcher(KeycloakService keycloakService, SessionStore<Session> sessionStore) {
        this.keycloakService = keycloakService;
        this.sessionStore = sessionStore;
    }

    @Override
    public Publisher<Authentication> fetchAuthentication(HttpRequest<?> request) {
        Optional<Session> sessionOpt = SessionForRequest.find(request);
        if (sessionOpt.isEmpty()) {
            return Mono.empty();
        }
        Session session = sessionOpt.get();
        Optional<String> username = session.get(KeycloakSessionPopulator.SESSION_USERNAME, String.class);
        if (username.isEmpty()) {
            return Mono.empty();
        }

        Optional<String> refreshToken = session.get(KeycloakAuthenticationProvider.REFRESH_TOKEN_ATTRIBUTE, String.class);
        Optional<Long> expiresAt = session.get(KeycloakAuthenticationProvider.EXPIRES_AT_ATTRIBUTE, Long.class);
        boolean expired = refreshToken.isPresent() && expiresAt.isPresent()
            && Instant.now().getEpochSecond() >= expiresAt.get() - EXPIRY_SKEW_SECONDS;

        if (!expired) {
            return Mono.just(buildAuthentication(session, username.get()));
        }

        return Mono.fromCallable(() -> keycloakService.refresh(refreshToken.get()))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(tokenResponse ->
            {
                if (tokenResponse.isEmpty()) {
                    log.info("Keycloak session is no longer valid; invalidating the server session {}", session.getId());
                    return Mono.fromCompletionStage(sessionStore.deleteSession(session.getId()))
                        .then(Mono.empty());
                }
                AccessTokenResponse token = tokenResponse.get();
                session.put(KeycloakAuthenticationProvider.REFRESH_TOKEN_ATTRIBUTE, token.getRefreshToken());
                session.put(
                    KeycloakAuthenticationProvider.EXPIRES_AT_ATTRIBUTE,
                    Instant.now().getEpochSecond() + token.getExpiresIn()
                );
                return Mono.just(buildAuthentication(session, username.get()));
            });
    }

    @SuppressWarnings("unchecked")
    private Authentication buildAuthentication(Session session, String username) {
        List<String> roles = session.get(KeycloakSessionPopulator.SESSION_ROLES, List.class)
            .map(list -> (List<String>) list)
            .orElseGet(List::of);
        Map<String, Object> attributes = new HashMap<>();
        session.get(KeycloakAuthenticationProvider.SUBJECT_ATTRIBUTE, String.class)
            .ifPresent(subject -> attributes.put(KeycloakAuthenticationProvider.SUBJECT_ATTRIBUTE, subject));
        return Authentication.build(username, roles, attributes);
    }
}
