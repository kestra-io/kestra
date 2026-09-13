package io.kestra.fethr.auth.keycloak;

import java.util.ArrayList;
import java.util.Map;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.session.SessionPopulator;
import io.micronaut.session.Session;
import jakarta.inject.Singleton;

/**
 * Stores everything {@link KeycloakSessionAuthenticationFetcher} needs to resolve the user from the session
 * on each request: the username and realm roles (to rebuild the {@code Authentication}), plus the realm
 * refresh token, the access-token expiry and the subject (for the KC-backed freshness check and logout).
 * Storing our own attributes lets the fetcher rebuild the Authentication without coupling to the framework's
 * internal session key. Runs alongside the framework's DefaultSessionPopulator (the SessionLoginHandler
 * invokes every SessionPopulator bean). Tokens stay server-side; only the opaque session id reaches the
 * browser. Active only with Micronaut Security on.
 */
@Singleton
@Requires(property = "micronaut.security.enabled", value = "true")
public class KeycloakSessionPopulator implements SessionPopulator<HttpRequest<?>> {
    public static final String SESSION_USERNAME = "kc.username";
    public static final String SESSION_ROLES = "kc.roles";

    @Override
    public void populateSession(HttpRequest<?> request, Authentication authentication, Session session) {
        session.put(SESSION_USERNAME, authentication.getName());
        session.put(SESSION_ROLES, new ArrayList<>(authentication.getRoles()));
        Map<String, Object> attributes = authentication.getAttributes();
        copy(session, attributes, KeycloakAuthenticationProvider.REFRESH_TOKEN_ATTRIBUTE);
        copy(session, attributes, KeycloakAuthenticationProvider.EXPIRES_AT_ATTRIBUTE);
        copy(session, attributes, KeycloakAuthenticationProvider.SUBJECT_ATTRIBUTE);
    }

    private void copy(Session session, Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (value != null) {
            session.put(key, value);
        }
    }
}
