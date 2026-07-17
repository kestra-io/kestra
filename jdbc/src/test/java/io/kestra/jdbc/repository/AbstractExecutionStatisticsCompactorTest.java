package io.kestra.jdbc.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.devskiller.friendly_id.FriendlyId;

import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionStatistic;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionStatisticsRepositoryInterface;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@Property(name = "kestra.server-type", value = "STANDALONE")
// The compactor is a live @Scheduled singleton here; without disabling the schedule a background
// tick can call compact() concurrently with the test's manual compact(), and both hit the shared
// singleton jOOQ Configuration from two threads -> ConcurrentModificationException. Push the
// schedule far past any test run so only the manual calls exercise compact().
@Property(name = "kestra.jdbc.execution-statistics.compactor.initial-delay", value = "999d")
@Property(name = "kestra.jdbc.execution-statistics.compactor.fixed-delay", value = "999d")
public abstract class AbstractExecutionStatisticsCompactorTest {
    @Inject
    protected ExecutionStatisticsRepositoryInterface executionStatisticsRepository;

    @Inject
    protected ExecutionStatisticsCompactor executionStatisticsCompactor;

    @Test
    void shouldCompactClosedBucketWithoutChangingReadResult() {
        // Given: 3 raw rows in a bucket from 5 minutes ago (closed relative to "now")
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);

        executionStatisticsRepository.saveBatch(
            List.of(
                raw(tenant, bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 1000),
                raw(tenant, bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 2000),
                raw(tenant, bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 3000)
            )
        );

        DailyExecutionStatistics before = statisticsFor(tenant, bucket);

        // When
        executionStatisticsCompactor.compact();

        // Then: the compaction is transparent to readers
        DailyExecutionStatistics after = statisticsFor(tenant, bucket);
        assertThat(after.getExecutionCounts()).isEqualTo(before.getExecutionCounts());
        assertThat(after.getDuration().getSum()).isEqualTo(before.getDuration().getSum());
        assertThat(after.getDuration().getMin()).isEqualTo(before.getDuration().getMin());
        assertThat(after.getDuration().getMax()).isEqualTo(before.getDuration().getMax());
    }

    @Test
    void shouldNotCompactTheCurrentOpenBucket() {
        // Given: a raw row in the current (not yet closed) minute
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        // The compactor derives closedBefore = Instant.now().truncatedTo(MINUTES) inside compact(); if a
        // minute rollover lands between the bucket built here and that sampling, this still-open bucket
        // would look closed and get compacted (count 2). Start early in a fresh minute so save + compact()
        // can't straddle a rollover.
        Await.until(() -> Instant.now().toEpochMilli() % 60_000 < 55_000);
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        String executionId = FriendlyId.createFriendlyId();
        executionStatisticsRepository.save(raw(tenant, bucket, State.Type.SUCCESS, executionId, 1000));

        // When
        executionStatisticsCompactor.compact();

        // Then: the raw row was left untouched (not claimed/deleted), so re-saving the same
        // executionId still overwrites it in place instead of adding a second (raw + aggregate) row
        executionStatisticsRepository.save(raw(tenant, bucket, State.Type.SUCCESS, executionId, 1000));
        DailyExecutionStatistics stats = statisticsFor(tenant, bucket);
        assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(1L);
    }

    @Test
    void shouldMergeLateRawRowsIntoAnExistingAggregate() {
        // Given: a bucket already compacted (2 executions), then a late-arriving raw row for the same bucket
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);

        executionStatisticsRepository.saveBatch(
            List.of(
                raw(tenant, bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 1000),
                raw(tenant, bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 2000)
            )
        );
        executionStatisticsCompactor.compact();

        executionStatisticsRepository.save(raw(tenant, bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 9000));

        // When: compacting again must merge the late row into the existing aggregate, not overwrite it
        executionStatisticsCompactor.compact();

        // Then
        DailyExecutionStatistics stats = statisticsFor(tenant, bucket);
        assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(3L);
        assertThat(stats.getDuration().getSum().toMillis()).isEqualTo(12000L);
        assertThat(stats.getDuration().getMax().toMillis()).isEqualTo(9000L);
        assertThat(stats.getTaskRunsDuration().getSum().toMillis()).isEqualTo(12000L);
        assertThat(stats.getTaskRunsDuration().getMin().toMillis()).isEqualTo(1000L);
        assertThat(stats.getTaskRunsDuration().getMax().toMillis()).isEqualTo(9000L);
    }

    @Test
    void shouldNotLetLateExecutionsWithoutTaskRunsCorruptTheCompactedTaskRunDurationMinMax() {
        // Given: a bucket already compacted from a single 500ms-task-run execution, then a
        // late-arriving execution that never ran any task
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);

        executionStatisticsRepository.save(raw(tenant, bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 500));
        executionStatisticsCompactor.compact();

        ExecutionStatistic lateWithoutTaskRuns = new ExecutionStatistic(
            tenant, "namespace", "flow", bucket, State.Type.FAILED, 1, 200, 200, 200, 0, 0, null, null, FriendlyId.createFriendlyId()
        );
        executionStatisticsRepository.save(lateWithoutTaskRuns);

        // When: compacting again must merge the task-less execution without dragging the min to 0
        executionStatisticsCompactor.compact();

        // Then
        DailyExecutionStatistics stats = statisticsFor(tenant, bucket);
        assertThat(stats.getExecutionCounts().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(2L);
        assertThat(stats.getTaskRunsDuration().getCount()).isEqualTo(1L);
        assertThat(stats.getTaskRunsDuration().getMin().toMillis()).isEqualTo(500L);
        assertThat(stats.getTaskRunsDuration().getMax().toMillis()).isEqualTo(500L);
    }

    @Test
    @Property(name = "kestra.jdbc.execution-statistics.compactor.max-keys-per-run", value = "2")
    void shouldDrainMoreKeysThanTheBatchLimitWithinASingleTick() {
        // Given: 5 distinct (namespace, flow, state) keys, more than the batch limit of 2 configured
        // for this test — a single compact() call must not stop after the first batch, or a burst of
        // activity spanning more distinct keys than the limit would fall further and further behind.
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);

        executionStatisticsRepository.saveBatch(
            IntStream.range(0, 5)
                .mapToObj(i -> raw(tenant, "flow-" + i, bucket, State.Type.SUCCESS, FriendlyId.createFriendlyId(), 1000))
                .toList()
        );

        // When
        executionStatisticsCompactor.compact();

        // Then: every key was compacted in this single tick, not just the first 2
        for (int i = 0; i < 5; i++) {
            DailyExecutionStatistics stats = statisticsFor(tenant, "flow-" + i, bucket);
            assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(1L);
        }
    }

    private DailyExecutionStatistics statisticsFor(String tenant, Instant bucket) {
        return statisticsFor(tenant, "flow", bucket);
    }

    private DailyExecutionStatistics statisticsFor(String tenant, String flowId, Instant bucket) {
        List<DailyExecutionStatistics> stats = executionStatisticsRepository.statistics(
            tenant, "namespace", flowId, bucket, bucket, DateUtils.GroupType.MINUTE
        );
        assertThat(stats).hasSize(1);
        return stats.getFirst();
    }

    private ExecutionStatistic raw(String tenant, Instant bucket, State.Type state, String executionId, long durationMs) {
        return raw(tenant, "flow", bucket, state, executionId, durationMs);
    }

    private ExecutionStatistic raw(String tenant, String flowId, Instant bucket, State.Type state, String executionId, long durationMs) {
        return new ExecutionStatistic(tenant, "namespace", flowId, bucket, state, 1, durationMs, durationMs, durationMs, 1, durationMs, durationMs, durationMs, executionId);
    }
}
