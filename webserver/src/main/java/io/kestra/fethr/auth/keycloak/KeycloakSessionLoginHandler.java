package io.kestra.fethr.auth.keycloak;

import java.util.List;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.config.RedirectConfiguration;
import io.micronaut.security.config.RedirectService;
import io.micronaut.security.errors.PriorToLoginPersistence;
import io.micronaut.security.session.SessionLoginHandler;
import io.micronaut.security.session.SessionPopulator;
import io.micronaut.session.Session;
import io.micronaut.session.SessionStore;
import jakarta.inject.Singleton;

/**
 * The built-in {@link SessionLoginHandler}, with {@code micronaut.security.redirect.enabled=false},
 * returns 200 OK on a failed login (its non-redirecting branch). An API-driven SPA cannot distinguish
 * that from a successful login (success also returns 200, only with the SESSION cookie). We override the
 * failure to return 401 so the login form can surface invalid credentials, matching the
 * {@code /login/authFailed} contract. Success/refresh keep the framework behavior (200 + opaque SESSION
 * cookie). Active only with Micronaut Security on.
 */
@Singleton
@Replaces(SessionLoginHandler.class)
@Requires(property = "micronaut.security.enabled", value = "true")
public class KeycloakSessionLoginHandler extends SessionLoginHandler {

    public KeycloakSessionLoginHandler(
        RedirectConfiguration redirectConfiguration,
        SessionStore<Session> sessionStore,
        @Nullable PriorToLoginPersistence<HttpRequest<?>, MutableHttpResponse<?>> priorToLoginPersistence,
        RedirectService redirectService,
        List<SessionPopulator<HttpRequest<?>>> sessionPopulators) {
        super(redirectConfiguration, sessionStore, priorToLoginPersistence, redirectService, sessionPopulators);
    }

    @Override
    public MutableHttpResponse<?> loginFailed(AuthenticationResponse authenticationResponse, HttpRequest<?> request) {
        return HttpResponse.unauthorized();
    }
}
