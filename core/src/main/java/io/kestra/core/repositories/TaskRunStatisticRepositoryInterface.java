package io.kestra.core.repositories;

import java.time.Instant;
import java.util.List;

import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.tasks.TaskRunStatistic;
import io.kestra.core.runners.IndexingRepository;
import io.kestra.core.utils.DateUtils;

import io.micronaut.core.annotation.Nullable;

/**
 * Repository for the pre-aggregated taskrun-statistics table.
 * <p>
 * Only {@link IndexingRepository#saveBatch} is used by the indexer to persist incoming raw rows.
 * {@link #statistics} exposes the aggregated read path that consumers (dashboards, SLA tracking,
 * task performance monitoring) should use instead of querying the {@code task_runs} table directly.
 */
public interface TaskRunStatisticRepositoryInterface extends IndexingRepository<TaskRunStatistic> {

    /**
     * Aggregates pre-compacted task run statistics for a given time range and grouping level.
     *
     * @param tenantId  the tenant identifier.
     * @param namespace optional filter by namespace.
     * @param flowId    optional filter by flow ID.
     * @param taskId    optional filter by task ID.
     * @param startDate start boundary of the time window.
     * @param endDate   end boundary of the time window.
     * @param groupBy   time aggregation level (e.g., MINUTE, HOUR, DAY).
     * @return a list of daily/time-bucket aggregated statistics points for chart rendering.
     */
    List<DailyExecutionStatistics> statistics(
        String tenantId,
        @Nullable String namespace,
        @Nullable String flowId,
        @Nullable String taskId,
        Instant startDate,
        Instant endDate,
        DateUtils.GroupType groupBy
    );
}