package io.kestra.core.repositories;

import java.time.Instant;
import java.util.List;

import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionStatistic;
import io.kestra.core.runners.IndexingRepository;
import io.kestra.core.utils.DateUtils;

import io.micronaut.core.annotation.Nullable;

/**
 * Repository for the pre-aggregated execution-statistics table (see {@link ExecutionStatistic}).
 * <p>
 * Only {@link IndexingRepository#saveBatch} is used by the indexer to persist incoming raw rows.
 * {@link #statistics} exposes the aggregated read path that consumers (dashboards, SLA tracking,
 * execution progress) should use instead of querying the {@code executions} table directly.
 */
public interface ExecutionStatisticsRepositoryInterface extends IndexingRepository<ExecutionStatistic> {
    /**
     * Aggregates execution statistics over a date range, bucketed by {@code groupBy}.
     * <p>
     * Transparently sums both raw rows (not yet compacted) and already-compacted aggregate rows
     * of the same bucket, so results are correct regardless of whether the periodic compaction job
     * has already processed that bucket.
     *
     * @param tenantId the tenant id.
     * @param namespace an optional namespace filter.
     * @param flowId an optional flow id filter (requires {@code namespace} to be set).
     * @param startDate the inclusive range start.
     * @param endDate the inclusive range end.
     * @param groupBy the bucket size to group results by.
     * @return one {@link DailyExecutionStatistics} entry per bucket in the range.
     */
    List<DailyExecutionStatistics> statistics(
        String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        Instant startDate,
        Instant endDate,
        DateUtils.GroupType groupBy);

    /**
     * Aggregates execution statistics over a date range, bucketed by {@code groupBy} for all tenants.
     * <p>
     * Transparently sums both raw rows (not yet compacted) and already-compacted aggregate rows
     * of the same bucket, so results are correct regardless of whether the periodic compaction job
     * has already processed that bucket.
     *
     * @param startDate the inclusive range start.
     * @param endDate the inclusive range end.
     * @param groupBy the bucket size to group results by.
     * @return one {@link DailyExecutionStatistics} entry per bucket in the range.
     */
    List<DailyExecutionStatistics> statisticsForAllTenants(
        Instant startDate,
        Instant endDate,
        DateUtils.GroupType groupBy);
}
