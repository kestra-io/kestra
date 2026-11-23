package io.kestra.webserver.configuration;

import io.kestra.webserver.utils.RequestUtils;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;

/**
 * Configuration for the {@code Secure} attribute of the cookies Kestra issues (auth, CSRF, and, in the
 * Enterprise Edition, the JWT/OIDC session cookies).
 *
 * @param secure forces the {@code Secure} attribute on or off; left unset, it is derived per request from
 *        {@link RequestUtils#isSecure(HttpRequest)}, which also trusts a TLS-terminating reverse proxy's
 *        {@code Forwarded}/{@code X-Forwarded-Proto} header.
 */
@ConfigurationProperties("kestra.webserver.cookies")
public record CookiesConfiguration(@Nullable Boolean secure) {
    public boolean isSecure(HttpRequest<?> request) {
        return secure != null ? secure : RequestUtils.isSecure(request);
    }
}
