package io.kestra.jdbc.repository;

import com.devskiller.friendly_id.FriendlyId;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.TaskRunStatistic;
import io.kestra.core.repositories.TaskRunStatisticRepositoryInterface;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.kestra.core.utils.Await.await;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

@MicronautTest
@Property(name = "kestra.server-type", value = "STANDALONE")
// The compactor is a live @Scheduled singleton here; without disabling the schedule a background
// tick can call compact() concurrently with the test's manual compact(), and both hit the shared
// singleton jOOQ Configuration from two threads -> ConcurrentModificationException. Push the
// schedule far past any test run so only the manual calls exercise compact().
@Property(name = "kestra.jdbc.task-run-statistics-statistics.compactor.initial-delay", value = "999d")
@Property(name = "kestra.jdbc.task-run-statistics-statistics.compactor.fixed-delay", value = "999d")
public class AbstractTaskRunStatisticsCompactorTest {

    @Inject
    protected TaskRunStatisticRepositoryInterface taskRunStatisticsRepository;

    @Inject
    protected TaskRunStatisticsCompactor taskRunStatisticsCompactor;

    @Test
    void shouldCompactClosedBucketWithoutChangingReadResult() {
        // Given: 3 raw rows in a bucket from 5 minutes ago (closed relative to "now")
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String namespace = "io.kestra.tests";
        String flowId = "my-flow";
        String taskId = "my-task";
        Instant bucket = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);

        taskRunStatisticsRepository.saveBatch(
            List.of(
                raw(tenant, namespace, flowId, taskId, bucket, State.Type.SUCCESS, 1000),
                raw(tenant, namespace, flowId, taskId, bucket, State.Type.SUCCESS, 2000),
                raw(tenant, namespace, flowId, taskId, bucket, State.Type.SUCCESS, 3000)
            )
        );

        DailyExecutionStatistics before = statisticsFor(tenant, namespace, flowId, taskId, bucket);
        assertThat(before).isNotNull();

        // When: run the compaction process
        taskRunStatisticsCompactor.compact();

        // Then: the compaction is transparent to readers (query returns identical aggregated results)
        DailyExecutionStatistics after = statisticsFor(tenant, namespace, flowId, taskId, bucket);
        assertThat(after).isNotNull();
        assertThat(after.getExecutionCounts()).isEqualTo(before.getExecutionCounts());
        assertThat(after.getDuration().getSum()).isEqualTo(before.getDuration().getSum());
        assertThat(after.getDuration().getMin()).isEqualTo(before.getDuration().getMin());
        assertThat(after.getDuration().getMax()).isEqualTo(before.getDuration().getMax());
    }

    @Test
    void shouldNotCompactTheCurrentOpenBucket() {
        // Given: a raw row in the current (not yet closed) minute bucket
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String namespace = "io.kestra.tests";
        String flowId = "my-flow";
        String taskId = "my-task";

        // The compactor derives closedBefore = Instant.now().truncatedTo(MINUTES) inside compact().
        // If a minute rollover lands between the bucket built here and that sampling, this still-open
        // bucket would look closed and get compacted. Ensure we are early in the current minute window.
        await().until(() -> Instant.now().toEpochMilli() % 60_000 < 55_000);

        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        String executionId = FriendlyId.createFriendlyId();
        String taskRunId = FriendlyId.createFriendlyId();

        taskRunStatisticsRepository.save(
            raw(tenant, namespace, flowId, taskId, bucket, State.Type.SUCCESS, executionId, taskRunId, 1000)
        );

        // When: run compaction while the bucket is still open
        taskRunStatisticsCompactor.compact();

        // Then: the raw row remains untouched (not deleted/compacted). Re-saving the exact same taskRunId
        // overwrites it in place instead of creating a second row (raw + aggregate), keeping the count = 1.
        taskRunStatisticsRepository.save(
            raw(tenant, namespace, flowId, taskId, bucket, State.Type.SUCCESS, executionId, taskRunId, 1000)
        );

        DailyExecutionStatistics stats = statisticsFor(tenant, namespace, flowId, taskId, bucket);
        assertThat(stats).isNotNull();
        assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(1L);
    }

    @Test
    void shouldMergeLateRawRowsIntoAnExistingAggregate() {
        // Given: a bucket already compacted (2 raw rows), then a late-arriving raw row for the same bucket
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String namespace = "io.kestra.tests";
        String flowId = "my-flow";
        String taskId = "my-task";
        Instant bucket = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);

        // Initial batch of raw rows
        taskRunStatisticsRepository.saveBatch(
            List.of(
                raw(tenant, namespace, flowId, taskId, bucket, State.Type.SUCCESS, 1000),
                raw(tenant, namespace, flowId, taskId, bucket, State.Type.SUCCESS, 2000)
            )
        );

        // Initial compaction into an aggregate record
        taskRunStatisticsCompactor.compact();

        // Late-arriving raw row for the already-compacted bucket
        taskRunStatisticsRepository.save(
            raw(tenant, namespace, flowId, taskId, bucket, State.Type.SUCCESS, 9000)
        );

        // When: compacting again must merge the late row into the existing aggregate without overwriting pre-existing counts
        taskRunStatisticsCompactor.compact();

        // Then
        DailyExecutionStatistics stats = statisticsFor(tenant, namespace, flowId, taskId, bucket);
        assertThat(stats).isNotNull();
        assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(3L);
        assertThat(stats.getDuration().getSum().toMillis()).isEqualTo(12000L);
        assertThat(stats.getDuration().getMin().toMillis()).isEqualTo(1000L);
        assertThat(stats.getDuration().getMax().toMillis()).isEqualTo(9000L);
    }

    @Test
    void shouldNotCorruptMinMaxDurationsWhenMergingLateTaskRuns() {
        // Given: a bucket already compacted with a 500ms task run
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String namespace = "io.kestra.tests";
        String flowId = "my-flow";
        String taskId = "my-task";
        Instant bucket = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);

        taskRunStatisticsRepository.save(
            raw(tenant, namespace, flowId, taskId, bucket, State.Type.SUCCESS, 500)
        );
        taskRunStatisticsCompactor.compact();

        // Late-arriving raw task run for the same bucket with a shorter duration (200ms) and different state
        taskRunStatisticsRepository.save(
            raw(tenant, namespace, flowId, taskId, bucket, State.Type.FAILED, 200)
        );

        // When: compacting again merges the late row into the bucket
        taskRunStatisticsCompactor.compact();

        // Then: global aggregated durations across all states in the bucket correctly reflect min=200ms, max=500ms
        DailyExecutionStatistics stats = statisticsFor(tenant, namespace, flowId, taskId, bucket);
        assertThat(stats).isNotNull();
        assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(1L);
        assertThat(stats.getExecutionCounts().get(State.Type.FAILED)).isEqualTo(1L);
        assertThat(stats.getDuration().getSum().toMillis()).isEqualTo(700L);
        assertThat(stats.getDuration().getMin().toMillis()).isEqualTo(200L);
        assertThat(stats.getDuration().getMax().toMillis()).isEqualTo(500L);
    }

    @Test
    @Property(name = "kestra.jdbc.task-run-statistics.compactor.max-keys-per-run", value = "2")
    void shouldDrainMoreKeysThanTheBatchLimitWithinASingleTick() {
        // Given: 5 distinct keys (e.g., different flows), exceeding the configured batch limit of 2.
        // A single compact() invocation must continue fetching and compacting in a loop until all keys
        // are drained, rather than stopping after the first batch.
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String namespace = "io.kestra.tests";
        String taskId = "task-1";
        Instant bucket = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);

        taskRunStatisticsRepository.saveBatch(
            IntStream.range(0, 5)
                .mapToObj(i -> raw(
                    tenant,
                    namespace,
                    "flow-" + i,
                    taskId,
                    bucket,
                    State.Type.SUCCESS,
                    1000
                ))
                .toList()
        );

        // When: trigger a single compaction tick
        taskRunStatisticsCompactor.compact();

        // Then: every key across all batches was compacted in this single tick
        for (int i = 0; i < 5; i++) {
            DailyExecutionStatistics stats = statisticsFor(tenant, namespace, "flow-" + i, taskId, bucket);
            assertThat(stats).isNotNull();
            assertThat(stats.getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(1L);
        }
    }

// -------------------------------------------------------------------------------------------------
// Helper Methods with
// -------------------------------------------------------------------------------------------------

    /**
     * Creates a raw (uncompacted) TaskRunStatistic record with unique executionId and taskRunId.
     */
    private TaskRunStatistic raw(
        String tenant,
        String namespace,
        String flowId,
        String taskId,
        Instant bucket,
        State.Type state,
        String executionId,
        String taskRunId,
        long durationMs
    ) {
        return new TaskRunStatistic(
            tenant,
            namespace,
            flowId,
            taskId,
            bucket,
            state,
            1L,          // count
            durationMs,  // durationSumMs
            durationMs,  // durationMinMs
            durationMs,  // durationMaxMs
            executionId, // non-null executionId marks it as raw
            taskRunId    // non-null taskRunId marks it as raw
        );
    }


    /**
     * Overloaded, Creates a raw (uncompacted) TaskRunStatistic record with any executionId and taskRunId.
     */
    private TaskRunStatistic raw(
        String tenant,
        String namespace,
        String flowId,
        String taskId,
        Instant bucket,
        State.Type state,
        long durationMs
    ) {
        return new TaskRunStatistic(
            tenant,
            namespace,
            flowId,
            taskId,
            bucket,
            state,
            1L,           // count
            durationMs,   // durationSumMs
            durationMs,   // durationMinMs
            durationMs,   // durationMaxMs
            FriendlyId.createFriendlyId(),
            FriendlyId.createFriendlyId()  // non-null taskRunId marks it as raw
        );
    }

    /**
     * Fetches the daily execution statistics summary for the specified minute bucket.
     */
    private DailyExecutionStatistics statisticsFor(
        String tenant,
        String namespace,
        String flowId,
        String taskId,
        Instant bucket
    ) {
        List<DailyExecutionStatistics> stats = taskRunStatisticsRepository.statistics(
            tenant,
            namespace,
            flowId,
            taskId,
            bucket,
            bucket,
            DateUtils.GroupType.MINUTE
        );
        return stats.isEmpty() ? null : stats.getFirst();
    }
}
