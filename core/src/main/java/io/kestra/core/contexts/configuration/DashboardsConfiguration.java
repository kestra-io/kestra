package io.kestra.core.contexts.configuration;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Limits on the queries dashboard charts run against the repositories.
 *
 * @param queryTimeout    how long a chart query may run when its dashboard sets no {@code queryTimeout}; {@code 0} disables the limit
 * @param maxQueryTimeout the longest a dashboard may ask for; a larger value is rejected when the dashboard is saved
 */
@ConfigurationProperties("kestra.dashboards")
public record DashboardsConfiguration(
    @Bindable(defaultValue = "30s") Duration queryTimeout,
    @Bindable(defaultValue = "5m") Duration maxQueryTimeout) {

    /** The limit to apply to a dashboard that asked for {@code requested}, capped at {@link #maxQueryTimeout()}. */
    public Duration resolveQueryTimeout(@Nullable Duration requested) {
        if (requested == null) {
            return queryTimeout;
        }
        return requested.compareTo(maxQueryTimeout) > 0 ? maxQueryTimeout : requested;
    }
}
