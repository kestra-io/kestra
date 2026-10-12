package io.kestra.core.repositories;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.devskiller.friendly_id.FriendlyId;

import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.tasks.TaskRunStatistic;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public abstract class AbstractTaskRunStatisticsRepositoryTest {

    @Inject
    protected TaskRunStatisticRepositoryInterface taskRunStatisticsRepository;

    /**
     * Hook for search-index / elasticsearch implementations to refresh index before querying.
     */
    protected void refresh() {
    }

    @Test
    void shouldBeIdempotentOnRedelivery() {
        // Given: the same raw row (same taskRunId) delivered twice, simulating an
        // at-least-once queue redelivery
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        String executionId = FriendlyId.createFriendlyId();
        String taskRunId = FriendlyId.createFriendlyId();

        TaskRunStatistic raw = raw(tenant, "namespace", "flow", "task", bucket, State.Type.SUCCESS, executionId, taskRunId, 1000);

        // When
        taskRunStatisticsRepository.save(raw);
        taskRunStatisticsRepository.save(raw);

        // Then: redelivery overwrote the same row instead of being double-counted
        DailyExecutionStatistics stats = statisticsFor(tenant, "namespace", "flow", "task", bucket);
        assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(1L);
        assertThat(stats.getDuration().getSum().toMillis()).isEqualTo(1000L);
    }

    @Test
    void shouldSumMultipleRawRowsInTheSameBucket() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        taskRunStatisticsRepository.saveBatch(
            List.of(
                raw(tenant, "namespace", "flow", "task", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), FriendlyId.createFriendlyId(), 1000),
                raw(tenant, "namespace", "flow", "task", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), FriendlyId.createFriendlyId(), 2000),
                raw(tenant, "namespace", "flow", "task", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), FriendlyId.createFriendlyId(), 3000)
            )
        );

        // When
        DailyExecutionStatistics stats = statisticsFor(tenant, "namespace", "flow", "task", bucket);

        // Then
        assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(3L);
        assertThat(stats.getDuration().getSum().toMillis()).isEqualTo(6000L);
        assertThat(stats.getDuration().getMin().toMillis()).isEqualTo(1000L);
        assertThat(stats.getDuration().getMax().toMillis()).isEqualTo(3000L);
        assertThat(stats.getDuration().getAvg().toMillis()).isEqualTo(2000L);
    }

    @Test
    void shouldAggregateAcrossRawAndAlreadyCompactedRows() {
        // Given: one already-compacted aggregate row (taskRunId == null) plus one raw row landing late
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        TaskRunStatistic compacted = new TaskRunStatistic(
            tenant, "namespace", "flow", "task", bucket, State.Type.SUCCESS, 5, 5000, 500, 1500, null, null
        );
        TaskRunStatistic lateRaw = raw(
            tenant, "namespace", "flow", "task", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), FriendlyId.createFriendlyId(), 2000
        );
        taskRunStatisticsRepository.saveBatch(List.of(compacted, lateRaw));

        // When
        DailyExecutionStatistics stats = statisticsFor(tenant, "namespace", "flow", "task", bucket);

        // Then: aggregations correctly reflect both compacted and raw rows
        assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(6L);
        assertThat(stats.getDuration().getSum().toMillis()).isEqualTo(7000L);
        assertThat(stats.getDuration().getMax().toMillis()).isEqualTo(2000L);
    }

    @Test
    void shouldScopeStatisticsToNamespaceFlowAndTask() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        taskRunStatisticsRepository.saveBatch(
            List.of(
                raw(tenant, "namespace", "flow", "target-task", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), FriendlyId.createFriendlyId(), 1000),
                raw(tenant, "namespace", "flow", "other-task", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), FriendlyId.createFriendlyId(), 1000),
                raw(tenant, "namespace", "other-flow", "target-task", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), FriendlyId.createFriendlyId(), 1000),
                raw(tenant, "other-namespace", "flow", "target-task", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), FriendlyId.createFriendlyId(), 1000)
            )
        );

        // When
        DailyExecutionStatistics stats = statisticsFor(tenant, "namespace", "flow", "target-task", bucket);

        // Then: only statistics for the matching namespace, flow, and task are returned
        assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(1L);
    }

    @Test
    void shouldAggregateAcrossTasksInFlowWhenTaskIdIsNull() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        taskRunStatisticsRepository.saveBatch(
            List.of(
                raw(tenant, "namespace", "flow", "task-1", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), FriendlyId.createFriendlyId(), 1000),
                raw(tenant, "namespace", "flow", "task-2", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), FriendlyId.createFriendlyId(), 2000)
            )
        );

        // When: passing taskId = null to aggregate across all tasks in the flow
        this.refresh();
        List<DailyExecutionStatistics> stats = taskRunStatisticsRepository.statistics(
            tenant, "namespace", "flow", null, bucket, bucket, DateUtils.GroupType.MINUTE
        );

        // Then
        assertThat(stats).hasSize(1);
        assertThat(stats.getFirst().getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(2L);
        assertThat(stats.getFirst().getDuration().getSum().toMillis()).isEqualTo(3000L);
    }

    private DailyExecutionStatistics statisticsFor(String tenant, String namespace, String flowId, String taskId, Instant bucket) {
        this.refresh();

        List<DailyExecutionStatistics> stats = taskRunStatisticsRepository.statistics(
            tenant, namespace, flowId, taskId, bucket, bucket, DateUtils.GroupType.MINUTE
        );
        assertThat(stats).hasSize(1);
        return stats.getFirst();
    }

    private TaskRunStatistic raw(
        String tenant,
        String namespace,
        String flowId,
        String taskId,
        Instant bucket,
        State.Type state,
        String executionId,
        String taskRunId,
        long durationMs) {

        return new TaskRunStatistic(
            tenant,
            namespace,
            flowId,
            taskId,
            bucket,
            state,
            1,
            durationMs,
            durationMs,
            durationMs,
            executionId,
            taskRunId
        );
    }
}