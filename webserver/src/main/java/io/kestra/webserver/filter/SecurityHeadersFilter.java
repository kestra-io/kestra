package io.kestra.webserver.filter;

import java.util.Objects;

import io.kestra.webserver.configuration.SecurityHeadersConfiguration;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;

/**
 * Adds browser security response headers to every HTTP response (UI and API), driven by
 * {@link SecurityHeadersConfiguration}.
 * <p>
 * Runs at {@link ServerFilterPhase#FIRST}, i.e. as the outermost filter, so its response processing happens
 * <em>last</em> on the way out — after every other filter, including ones that replace the response object
 * wholesale (e.g. {@link io.kestra.webserver.controllers.api.StaticFilter} rebuilding the {@code index.html}
 * response) or short-circuit the chain early with an error response (e.g. an authentication or CSRF filter
 * returning 401/403 without proceeding). Any other phase would let such responses skip this filter entirely.
 * Each configured, non-blank header is only set when it is not already present, so a controller or another
 * filter can still override it. HSTS is emitted only on secure (HTTPS) requests, since it is meaningless — and
 * potentially harmful — over plain HTTP. Note this only detects TLS terminated on this server directly; behind a
 * TLS-terminating reverse proxy, configure the proxy to also set HSTS, since the request reaches this server as
 * plain HTTP.
 */
@Requires(property = "kestra.webserver.security-headers.enabled", notEquals = "false", defaultValue = "true")
@ServerFilter("/**")
public class SecurityHeadersFilter implements Ordered {
    private static final String X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String REFERRER_POLICY = "Referrer-Policy";
    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String CONTENT_SECURITY_POLICY_REPORT_ONLY = "Content-Security-Policy-Report-Only";
    private static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";

    @Nullable
    private final String frameOptions;
    @Nullable
    private final String contentTypeOptions;
    @Nullable
    private final String referrerPolicy;
    @Nullable
    private final String contentSecurityPolicy;
    private final String contentSecurityPolicyHeaderName;
    @Nullable
    private final String strictTransportSecurity;

    public SecurityHeadersFilter(SecurityHeadersConfiguration configuration) {
        Objects.requireNonNull(configuration);
        this.frameOptions = blankToNull(configuration.frameOptions());
        this.contentTypeOptions = blankToNull(configuration.contentTypeOptions());
        this.referrerPolicy = blankToNull(configuration.referrerPolicy());
        this.contentSecurityPolicy = blankToNull(configuration.contentSecurityPolicy());
        this.contentSecurityPolicyHeaderName = configuration.contentSecurityPolicyReportOnly()
            ? CONTENT_SECURITY_POLICY_REPORT_ONLY
            : CONTENT_SECURITY_POLICY;
        this.strictTransportSecurity = blankToNull(configuration.strictTransportSecurity());
    }

    @ResponseFilter
    public void addSecurityHeaders(@NonNull HttpRequest<?> request, @NonNull MutableHttpResponse<?> response) {
        setIfAbsent(response, X_FRAME_OPTIONS, frameOptions);
        setIfAbsent(response, X_CONTENT_TYPE_OPTIONS, contentTypeOptions);
        setIfAbsent(response, REFERRER_POLICY, referrerPolicy);
        setIfAbsent(response, contentSecurityPolicyHeaderName, contentSecurityPolicy);

        // HSTS is only meaningful over HTTPS; never advertise it on plain HTTP.
        if (request.isSecure()) {
            setIfAbsent(response, STRICT_TRANSPORT_SECURITY, strictTransportSecurity);
        }
    }

    private static void setIfAbsent(MutableHttpResponse<?> response, String name, @Nullable String value) {
        if (value != null && !response.getHeaders().contains(name)) {
            response.getHeaders().set(name, value);
        }
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.FIRST.order();
    }
}
