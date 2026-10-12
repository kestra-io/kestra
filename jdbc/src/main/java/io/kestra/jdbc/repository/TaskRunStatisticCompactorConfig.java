package io.kestra.jdbc.repository;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for {@link TaskRunStatisticsCompactor}.
 *
 * @param initialDelay delay before the first compaction run after startup.
 * @param fixedDelay delay between the end of one compaction run and the start of the next.
 * @param maxKeysPerRun maximum number of distinct {@code (tenant, namespace, flow, task, state)} keys
 *                     compacted per run, bounding the work of a single tick.
 */
@ConfigurationProperties("kestra.jdbc.task-run-statistics.compactor")
public record TaskRunStatisticCompactorConfig(
    @Bindable(defaultValue = "1m") Duration initialDelay,
    @Bindable(defaultValue = "1m") Duration fixedDelay,
    @Bindable(defaultValue = "1000") Integer maxKeysPerRun
) {
}





