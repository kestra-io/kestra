package io.kestra.indexer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import com.devskiller.friendly_id.FriendlyId;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.statistics.DailyExecutionStatistics;
import io.kestra.core.models.executions.statistics.ExecutionStatistic;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.repositories.*;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@KestraTest
class DefaultIndexerTest {
    @Inject
    private DefaultIndexer indexer;

    @Inject
    private DispatchQueueInterface<ExecutionStatistic> executionStatisticQueue;

    @Inject
    private ExecutionStatisticsRepositoryInterface executionStatisticsRepository;

    @Inject
    private DispatchQueueInterface<LogEntry> logQueue;

    @Inject
    private LogDataStoreInterface logDataStore;

    @Inject
    private DispatchQueueInterface<MetricEntry> metricQueue;

    @Inject
    private MetricRepositoryInterface metricRepository;

    @Test
    void shouldPersistExecutionStatisticsConsumedFromTheQueue() throws Exception {
        // Given
        indexer.startQueues();

        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant bucket = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        ExecutionStatistic statistic = new ExecutionStatistic(
            tenant, "namespace", "flow", bucket, State.Type.SUCCESS, 1, 1000, 1000, 1000, 1, 1000, 1000L, 1000L, FriendlyId.createFriendlyId()
        );

        // When
        executionStatisticQueue.emit(statistic);

        // Then: the indexer batch-consumes the queue asynchronously and persists it via saveBatch
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        {
            List<DailyExecutionStatistics> stats = executionStatisticsRepository.statistics(
                tenant, "namespace", "flow", bucket, bucket, DateUtils.GroupType.MINUTE
            );
            assertThat(stats).hasSize(1);
            assertThat(stats.getFirst().getExecutionCounts().get(State.Type.SUCCESS)).isEqualTo(1L);
        });
    }

    @Test
    void shouldPersistLogsConsumedFromTheQueue() throws Exception {
        // Given
        indexer.startQueues();

        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();
        LogEntry logEntry = LogEntry.builder()
            .tenantId(tenant)
            .namespace("namespace")
            .flowId("flow")
            .taskId("task")
            .executionId(executionId)
            .taskRunId(FriendlyId.createFriendlyId())
            .attemptNumber(0)
            .timestamp(Instant.now())
            .level(Level.INFO)
            .thread("")
            .message("hello from the indexer test")
            .build();

        // When
        logQueue.emit(logEntry);

        // Then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        {
            List<LogEntry> logs = logDataStore.findByExecutionIdWithoutAcl(tenant, executionId, Level.INFO);
            assertThat(logs).hasSize(1);
            assertThat(logs.getFirst().getMessage()).isEqualTo("hello from the indexer test");
        });
    }

    @Test
    void shouldPersistMetricsConsumedFromTheQueue() throws Exception {
        // Given
        indexer.startQueues();

        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();
        TaskRun taskRun = TaskRun.builder()
            .tenantId(tenant)
            .namespace("namespace")
            .flowId("flow")
            .executionId(executionId)
            .taskId("task")
            .id(FriendlyId.createFriendlyId())
            .build();
        MetricEntry metricEntry = MetricEntry.of(taskRun, Counter.of("counter", 1), null);

        // When
        metricQueue.emit(metricEntry);

        // Then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        {
            ArrayListTotal<MetricEntry> metrics = metricRepository.findByExecutionId(tenant, executionId, Pageable.from(1, 10));
            assertThat(metrics).hasSize(1);
            assertThat(metrics.getFirst().getName()).isEqualTo("counter");
        });
    }
}
