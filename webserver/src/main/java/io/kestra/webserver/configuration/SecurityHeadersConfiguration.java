package io.kestra.webserver.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for the browser security response headers emitted on every HTTP response by
 * {@link io.kestra.webserver.filter.SecurityHeadersFilter}.
 * <p>
 * A blank value for any individual header disables that header, while {@code enabled: false} disables the
 * whole filter (also read directly by a {@code @Requires} on the filter itself, so the filter bean isn't even
 * created when disabled).
 *
 * @param enabled master toggle for the whole filter (default {@code true}).
 * @param frameOptions value for {@code X-Frame-Options} (default {@code SAMEORIGIN}); blank to disable.
 * @param contentTypeOptions value for {@code X-Content-Type-Options} (default {@code nosniff}); blank to disable.
 * @param referrerPolicy value for {@code Referrer-Policy} (default {@code strict-origin-when-cross-origin}); blank to disable.
 * @param contentSecurityPolicy value for {@code Content-Security-Policy}; opt-in, absent by default.
 * @param contentSecurityPolicyReportOnly when {@code true}, the CSP is emitted under
 *        {@code Content-Security-Policy-Report-Only} instead of enforcing it — a safe intermediate step.
 * @param strictTransportSecurity value for {@code Strict-Transport-Security}; opt-in, and only emitted on HTTPS requests.
 */
@ConfigurationProperties("kestra.webserver.security-headers")
public record SecurityHeadersConfiguration(
    @Bindable(defaultValue = "true") boolean enabled,
    @Bindable(defaultValue = "SAMEORIGIN") String frameOptions,
    @Bindable(defaultValue = "nosniff") String contentTypeOptions,
    @Bindable(defaultValue = "strict-origin-when-cross-origin") String referrerPolicy,
    @Nullable String contentSecurityPolicy,
    @Bindable(defaultValue = "false") boolean contentSecurityPolicyReportOnly,
    @Nullable String strictTransportSecurity) {
}
