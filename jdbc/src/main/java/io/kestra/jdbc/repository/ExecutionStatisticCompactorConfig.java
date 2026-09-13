package io.kestra.jdbc.repository;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for {@link ExecutionStatisticsCompactor}.
 *
 * @param initialDelay delay before the first compaction run after startup.
 * @param fixedDelay delay between the end of one compaction run and the start of the next.
 * @param maxKeysPerRun maximum number of distinct {@code (tenant, namespace, flow, state)} keys
 *        compacted per run, bounding the work of a single tick.
 */
@ConfigurationProperties("kestra.jdbc.execution-statistics.compactor")
public record ExecutionStatisticCompactorConfig(
    @Bindable(defaultValue = "1m") Duration initialDelay, // kept here for documentation, used inside the {@link ExecutionStatisticsCompactor}'s @Scheduled annotation
    @Bindable(defaultValue = "1m") Duration fixedDelay, // kept here for documentation, used inside the {@link ExecutionStatisticsCompactor}'s @Scheduled annotation
    @Bindable(defaultValue = "1000") Integer maxKeysPerRun) {
}
