package io.kestra.core.scheduler;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration properties for the scheduler.
 *
 * @param vnodes the total number of virtual nodes to distribute across schedulers (default: 16)
 * @param vnodesRebalanceTimeout the maximum duration to wait for scheduler replies during
 *        VNode rebalancing (default: 5 seconds)
 */
@ConfigurationProperties("kestra.scheduler")
public record SchedulerConfiguration(
    @Bindable(defaultValue = "16") Integer vnodes,
    @Bindable(defaultValue = "PT5S") Duration vnodesRebalanceTimeout,
    @Bindable(defaultValue = "100") Integer cacheMaxSizePerVNode) {

    public boolean isCacheDisable() {
        return cacheMaxSizePerVNode == null || cacheMaxSizePerVNode < 1;
    }
}
