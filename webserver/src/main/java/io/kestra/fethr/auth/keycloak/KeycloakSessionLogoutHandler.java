package io.kestra.fethr.auth.keycloak;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.config.RedirectConfiguration;
import io.micronaut.security.config.RedirectService;
import io.micronaut.security.session.SessionLogoutHandler;
import io.micronaut.session.Session;
import io.micronaut.session.SessionStore;
import io.micronaut.session.http.SessionForRequest;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Ends the Keycloak user-session when the user logs out, on top of the framework's {@link SessionLogoutHandler}.
 * The built-in handler does not know about Keycloak, so this subclass adds the realm logout (best-effort, off
 * the request thread) using the {@code sub} carried in the Authentication, and deletes the whole server session.
 * Deleting it explicitly is required because {@link KeycloakSessionAuthenticationFetcher} rebuilds the
 * Authentication from our own session attributes (username/roles/...), which the framework's logout does not
 * clear, so a cleared-but-not-deleted session would still authenticate. Active only with Micronaut Security on.
 */
@Singleton
@Replaces(SessionLogoutHandler.class)
@Requires(property = "micronaut.security.enabled", value = "true")
@Slf4j
public class KeycloakSessionLogoutHandler extends SessionLogoutHandler {
    private final KeycloakService keycloakService;
    private final SessionStore<Session> sessionStore;
    private final ExecutorService ioExecutor;

    public KeycloakSessionLogoutHandler(
        RedirectConfiguration redirectConfiguration,
        RedirectService redirectService,
        KeycloakService keycloakService,
        SessionStore<Session> sessionStore,
        @Named(TaskExecutors.IO) ExecutorService ioExecutor) {
        super(redirectConfiguration, redirectService);
        this.keycloakService = keycloakService;
        this.sessionStore = sessionStore;
        this.ioExecutor = ioExecutor;
    }

    @Override
    public MutableHttpResponse<?> logout(HttpRequest<?> request) {
        Optional<Session> sessionOpt = SessionForRequest.find(request);
        request.getUserPrincipal(Authentication.class).ifPresent(authentication ->
        {
            Object subject = authentication.getAttributes().get(KeycloakAuthenticationProvider.SUBJECT_ATTRIBUTE);
            if (subject != null) {
                ioExecutor.execute(() ->
                {
                    try {
                        keycloakService.logout(subject.toString());
                    } catch (Exception e) {
                        log.warn("Keycloak logout failed for user {} (best-effort, local session already cleared)", subject, e);
                    }
                });
            }
        });
        MutableHttpResponse<?> response = super.logout(request);
        sessionOpt.ifPresent(session -> sessionStore.deleteSession(session.getId()));
        return response;
    }
}
