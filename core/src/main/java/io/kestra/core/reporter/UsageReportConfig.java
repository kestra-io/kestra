package io.kestra.core.reporter;

import java.net.URI;
import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for anonymous usage reporting.
 * <p>
 * Bound from {@code kestra.anonymous-usage-report.*} properties.
 * Also used for gRPC propagation between controller and worker.
 */
@ConfigurationProperties("kestra." + UsageReportConfig.ANONYMOUS_USAGE_REPORT)
public record UsageReportConfig(
    @Bindable(defaultValue = "true") boolean enabled,
    @Bindable(defaultValue = DEFAULT_URI) URI uri,
    @Bindable(defaultValue = "5m") Duration initialDelay,
    @Bindable(defaultValue = "5m") Duration fixedDelay
) {

    public static final String ANONYMOUS_USAGE_REPORT = "anonymous-usage-report";
    
    public static final String DEFAULT_URI = "https://api.kestra.io/v1/reports/server-events";
    
    public static UsageReportConfig getDefault() {
        return new UsageReportConfig(
            true,
            URI.create(DEFAULT_URI),
            Duration.ofMinutes(5),
            Duration.ofMinutes(5)
        );
    }
}
