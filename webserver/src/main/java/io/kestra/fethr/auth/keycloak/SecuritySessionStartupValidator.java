package io.kestra.fethr.auth.keycloak;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Fails fast at startup when Micronaut Security is on but the session is not configured, so a misconfigured
 * deploy never boots half-secured (e.g. security enabled but still in the rejected token-cookie mode, or
 * without the session cookie). Mirrors the {@code @Context} + {@code @PostConstruct} pattern of
 * {@link WebserverService}: a context-scoped bean is instantiated at startup, so throwing here aborts boot.
 * Active only when {@code micronaut.security.enabled=true} (the Keycloak deployment).
 */
@Context
@Singleton
@Requires(property = "micronaut.security.enabled", value = "true")
@Slf4j
public class SecuritySessionStartupValidator {
    private final String authenticationMode;
    private final boolean sessionCookieEnabled;

    public SecuritySessionStartupValidator(
        @Property(name = "micronaut.security.authentication", defaultValue = "") String authenticationMode,
        @Property(name = "micronaut.session.http.cookie", defaultValue = "false") boolean sessionCookieEnabled) {
        this.authenticationMode = authenticationMode;
        this.sessionCookieEnabled = sessionCookieEnabled;
    }

    @PostConstruct
    void validate() {
        if (!"session".equalsIgnoreCase(authenticationMode)) {
            log.error(
                "Micronaut Security is enabled but micronaut.security.authentication is '{}', expected 'session'. "
                    + "The server-managed session auth is mandatory when security is on; refusing to start.",
                authenticationMode
            );
            throw new IllegalStateException(
                "micronaut.security.authentication must be 'session' when micronaut.security.enabled=true (got '"
                    + authenticationMode + "')."
            );
        }
        if (!sessionCookieEnabled) {
            log.error(
                "Micronaut Security session auth is enabled but micronaut.session.http.cookie is not true; "
                    + "the opaque SESSION cookie is required. Refusing to start."
            );
            throw new IllegalStateException(
                "micronaut.session.http.cookie must be true when micronaut.security.authentication=session."
            );
        }
    }
}
