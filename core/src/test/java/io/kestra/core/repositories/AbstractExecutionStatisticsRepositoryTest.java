package io.kestra.core.repositories;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.devskiller.friendly_id.FriendlyId;

import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionStatistic;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public abstract class AbstractExecutionStatisticsRepositoryTest {
    @Inject
    protected ExecutionStatisticsRepositoryInterface executionStatisticsRepository;

    /**
     * Hook for Elasticsearch impl to be able to refresh the index before querying
     */
    protected void refresh() {
    }

    @Test
    void shouldBeIdempotentOnRedelivery() {
        // Given: the same raw row (same executionId) delivered twice, simulating an
        // at-least-once queue redelivery
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        String executionId = FriendlyId.createFriendlyId();
        ExecutionStatistic raw = raw(tenant, "namespace", "flow", bucket, State.Type.SUCCESS, executionId, 1000);

        // When
        executionStatisticsRepository.save(raw);
        executionStatisticsRepository.save(raw);

        // Then: redelivery overwrote the same row instead of being double-counted
        DailyExecutionStatistics stats = statisticsFor(tenant, "namespace", "flow", bucket);
        assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(1L);
        assertThat(stats.getDuration().getSum().toMillis()).isEqualTo(1000L);
    }

    @Test
    void shouldSumMultipleRawRowsInTheSameBucket() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        executionStatisticsRepository.saveBatch(
            List.of(
                raw(tenant, "namespace", "flow", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 1000),
                raw(tenant, "namespace", "flow", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 2000),
                raw(tenant, "namespace", "flow", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 3000)
            )
        );

        // When
        DailyExecutionStatistics stats = statisticsFor(tenant, "namespace", "flow", bucket);

        // Then
        assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(3L);
        assertThat(stats.getDuration().getSum().toMillis()).isEqualTo(6000L);
        assertThat(stats.getDuration().getMin().toMillis()).isEqualTo(1000L);
        assertThat(stats.getDuration().getMax().toMillis()).isEqualTo(3000L);
        assertThat(stats.getDuration().getAvg().toMillis()).isEqualTo(2000L);
        assertThat(stats.getTaskRunsDuration().getSum().toMillis()).isEqualTo(6000L);
        assertThat(stats.getTaskRunsDuration().getMin().toMillis()).isEqualTo(1000L);
        assertThat(stats.getTaskRunsDuration().getMax().toMillis()).isEqualTo(3000L);
        assertThat(stats.getTaskRunsDuration().getAvg().toMillis()).isEqualTo(2000L);
    }

    @Test
    void shouldNotLetExecutionsWithoutTaskRunsCorruptTaskRunDurationMinMax() {
        // Given: one execution with a single 500ms task run, and one that never ran any task
        // (e.g. failed validation before starting) so it has no task-run duration to contribute
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        executionStatisticsRepository.saveBatch(
            List.of(
                raw(tenant, "namespace", "flow", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 500),
                rawWithoutTaskRuns(tenant, "namespace", "flow", bucket, State.Type.FAILED, FriendlyId.createFriendlyId(), 200)
            )
        );

        // When
        DailyExecutionStatistics stats = statisticsFor(tenant, "namespace", "flow", bucket);

        // Then: the task-less execution must not drag the min down to 0
        assertThat(stats.getTaskRunsDuration().getCount()).isEqualTo(1L);
        assertThat(stats.getTaskRunsDuration().getSum().toMillis()).isEqualTo(500L);
        assertThat(stats.getTaskRunsDuration().getMin().toMillis()).isEqualTo(500L);
        assertThat(stats.getTaskRunsDuration().getMax().toMillis()).isEqualTo(500L);
    }

    @Test
    void shouldReportNoTaskRunDurationWhenNoExecutionInTheBucketHadTaskRuns() {
        // Given: every execution in the bucket failed before running any task
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        executionStatisticsRepository.saveBatch(
            List.of(
                rawWithoutTaskRuns(tenant, "namespace", "flow", bucket, State.Type.FAILED, FriendlyId.createFriendlyId(), 100),
                rawWithoutTaskRuns(tenant, "namespace", "flow", bucket, State.Type.FAILED, FriendlyId.createFriendlyId(), 200)
            )
        );

        // When
        DailyExecutionStatistics stats = statisticsFor(tenant, "namespace", "flow", bucket);

        // Then: no task-run data point exists, so min/max must be null, not a false 0
        assertThat(stats.getTaskRunsDuration().getCount()).isEqualTo(0L);
        assertThat(stats.getTaskRunsDuration().getSum().toMillis()).isEqualTo(0L);
        assertThat(stats.getTaskRunsDuration().getMin()).isNull();
        assertThat(stats.getTaskRunsDuration().getMax()).isNull();
    }

    @Test
    void shouldAggregateAcrossRawAndAlreadyCompactedRows() {
        // Given: one already-compacted aggregate row (as the periodic compaction job would
        // produce, executionId == null) plus one raw row landing late in the same bucket
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        ExecutionStatistic compacted = new ExecutionStatistic(tenant, "namespace", "flow", bucket, State.Type.SUCCESS, 5, 5000, 500, 1500, 10, 5000, 100L, 900L, null);
        ExecutionStatistic lateRaw = raw(tenant, "namespace", "flow", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 2000);
        executionStatisticsRepository.saveBatch(List.of(compacted, lateRaw));

        // When
        DailyExecutionStatistics stats = statisticsFor(tenant, "namespace", "flow", bucket);

        // Then: the read is correct whether or not the compaction job has already processed this bucket
        assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(6L);
        assertThat(stats.getDuration().getSum().toMillis()).isEqualTo(7000L);
        assertThat(stats.getDuration().getMax().toMillis()).isEqualTo(2000L);
    }

    @Test
    void shouldScopeStatisticsToNamespaceAndFlow() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        executionStatisticsRepository.saveBatch(
            List.of(
                raw(tenant, "namespace", "flow", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 1000),
                raw(tenant, "namespace", "other-flow", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 1000),
                raw(tenant, "other-namespace", "flow", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 1000)
            )
        );

        // When
        DailyExecutionStatistics stats = statisticsFor(tenant, "namespace", "flow", bucket);

        // Then
        assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(1L);
    }

    @Test
    void shouldAggregateAcrossAllTenantsIgnoringTenantScoping() {
        // Given: two executions in the same bucket but belonging to different tenants
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);

        long before = allTenantsCountFor(bucket);

        executionStatisticsRepository.saveBatch(
            List.of(
                raw(tenant1, "namespace", "flow", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 1000),
                raw(tenant2, "namespace", "flow", bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 2000)
            )
        );

        // When
        long after = allTenantsCountFor(bucket);

        // Then: both tenants' executions were summed into the same bucket, unscoped by tenant
        assertThat(after - before).isEqualTo(2L);
    }

    private long allTenantsCountFor(Instant bucket) {
        this.refresh();

        List<DailyExecutionStatistics> stats = executionStatisticsRepository.statisticsForAllTenants(
            bucket, bucket, DateUtils.GroupType.MINUTE
        );

        if (stats.isEmpty()) {
            return 0L;
        }

        return stats.getFirst().getExecutionCounts().getOrDefault(State.Type.SUCCESS, 0L);
    }

    private DailyExecutionStatistics statisticsFor(String tenant, String namespace, String flowId, Instant bucket) {
        // refresh before querying
        this.refresh();

        List<DailyExecutionStatistics> stats = executionStatisticsRepository.statistics(
            tenant, namespace, flowId, bucket, bucket, DateUtils.GroupType.MINUTE
        );
        assertThat(stats).hasSize(1);
        return stats.getFirst();
    }

    private ExecutionStatistic raw(String tenant, String namespace, String flowId, Instant bucket, State.Type state, String executionId, long durationMs) {
        return new ExecutionStatistic(tenant, namespace, flowId, bucket, state, 1, durationMs, durationMs, durationMs, 1, durationMs, durationMs, durationMs, executionId);
    }

    private ExecutionStatistic rawWithoutTaskRuns(String tenant, String namespace, String flowId, Instant bucket, State.Type state, String executionId, long durationMs) {
        return new ExecutionStatistic(tenant, namespace, flowId, bucket, state, 1, durationMs, durationMs, durationMs, 0, 0, null, null, executionId);
    }
}
