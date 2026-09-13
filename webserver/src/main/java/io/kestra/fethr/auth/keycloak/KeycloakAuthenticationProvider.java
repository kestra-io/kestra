package io.kestra.fethr.auth.keycloak;

import java.text.ParseException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.keycloak.representations.AccessTokenResponse;

import com.nimbusds.jwt.SignedJWT;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestExecutorAuthenticationProvider;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Authenticates the SPA login (username + password) against Keycloak via Direct Grant, the custom
 * provider behind the built-in {@code /login} handler. The realm validates the password (and applies its
 * password policy + brute-force protection); on success the realm roles are extracted from the access
 * token and the refresh token is carried in the {@code Authentication} attributes so it persists in the
 * server-side session (the KC-backed binding: the session is refreshed against Keycloak and dies when the
 * Keycloak session does). The token never reaches the browser. Active only with Micronaut Security on; the
 * HL7 service authenticates with a Keycloak bearer token (validated against the realm JWKS), not basic auth.
 */
@Singleton
@Requires(property = "micronaut.security.enabled", value = "true")
@Slf4j
public class KeycloakAuthenticationProvider<B> implements HttpRequestExecutorAuthenticationProvider<B> {
    public static final String REFRESH_TOKEN_ATTRIBUTE = "refresh_token";
    public static final String SUBJECT_ATTRIBUTE = "sub";
    /** Epoch-second at which the realm access token expires; drives the KC-backed refresh in the session. */
    public static final String EXPIRES_AT_ATTRIBUTE = "expires_at";

    private final KeycloakService keycloakService;
    private final KeycloakRolesFinder rolesFinder;

    public KeycloakAuthenticationProvider(KeycloakService keycloakService, KeycloakRolesFinder rolesFinder) {
        this.keycloakService = keycloakService;
        this.rolesFinder = rolesFinder;
    }

    @Override
    public String getExecutorName() {
        // Run off the Netty event loop: signIn calls the blocking KeycloakTokenClient (Direct Grant),
        // which throws "BlockingHttpClient operation on a netty event loop thread" if run on the loop.
        return TaskExecutors.IO;
    }

    @Override
    public AuthenticationResponse authenticate(
        @Nullable HttpRequest<B> requestContext,
        AuthenticationRequest<String, String> authenticationRequest) {
        String username = authenticationRequest.getIdentity();
        String password = authenticationRequest.getSecret();

        Optional<AccessTokenResponse> token;
        try {
            token = keycloakService.signIn(username, password);
        } catch (Exception e) {
            // A provider must decline (return failure), not crash the request, when it cannot validate,
            // e.g. Keycloak is unreachable or misconfigured. Log for ops and let the chain resolve to 401.
            log.error("Keycloak sign-in errored for '{}'; declining authentication", username, e);
            return AuthenticationResponse.failure(AuthenticationFailureReason.UNKNOWN);
        }
        if (token.isEmpty()) {
            return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH);
        }

        AccessTokenResponse accessToken = token.get();
        // Surfaces the realm access token in the server logs for inspection. Kept at DEBUG on purpose: an
        // access token is a sensitive credential and must not reach INFO/prod logs; enable DEBUG on this
        // logger to view it.
        log.debug("Keycloak returned an access token for '{}': {}", username, accessToken.getToken());
        Map<String, Object> claims = parseClaims(accessToken.getToken());
        List<String> roles = rolesFinder.resolveRoles(claims);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(REFRESH_TOKEN_ATTRIBUTE, accessToken.getRefreshToken());
        attributes.put(EXPIRES_AT_ATTRIBUTE, Instant.now().getEpochSecond() + accessToken.getExpiresIn());
        Object subject = claims.get(SUBJECT_ATTRIBUTE);
        if (subject != null) {
            attributes.put(SUBJECT_ATTRIBUTE, subject.toString());
        }

        return AuthenticationResponse.success(username, roles, attributes);
    }

    private Map<String, Object> parseClaims(String accessToken) {
        try {
            return SignedJWT.parse(accessToken).getJWTClaimsSet().getClaims();
        } catch (ParseException e) {
            log.error("Could not parse the Keycloak access token claims for the authenticated user", e);
            return Map.of();
        }
    }
}
