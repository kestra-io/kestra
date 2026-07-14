package io.kestra.core.repositories;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.devskiller.friendly_id.FriendlyId;

import io.kestra.core.models.dashboards.AggregationType;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.MetricAggregations;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.dashboard.data.Metrics;
import io.kestra.plugin.core.dashboard.data.MetricsKPI;

import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public abstract class AbstractMetricRepositoryTest {
    @Inject
    protected MetricRepositoryInterface metricRepository;

    @Test
    void all() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();
        ZonedDateTime now = ZonedDateTime.now();
        TaskRun taskRun1 = taskRun(tenant, executionId, "task");
        MetricEntry counter = MetricEntry.of(taskRun1, counter("counter"), null);
        MetricEntry testCounter = MetricEntry.of(taskRun1, counter("test"), ExecutionKind.TEST);
        MetricEntry normalCounter = MetricEntry.of(taskRun1, counter("normal"), ExecutionKind.NORMAL);
        TaskRun taskRun2 = taskRun(tenant, executionId, "task");
        MetricEntry timer = MetricEntry.of(taskRun2, timer(), null);
        metricRepository.save(counter);
        metricRepository.save(testCounter); // should only be retrieved by execution id
        metricRepository.save(normalCounter);
        metricRepository.save(timer);

        List<MetricEntry> results = metricRepository.findByExecutionId(tenant, executionId, Pageable.from(1, 10));
        assertThat(results.size()).isEqualTo(4);

        results = metricRepository.findByExecutionIdAndTaskId(tenant, executionId, taskRun1.getTaskId(), Pageable.from(1, 10));
        assertThat(results.size()).isEqualTo(4);

        results = metricRepository.findByExecutionIdAndTaskRunId(tenant, executionId, taskRun1.getId(), Pageable.from(1, 10));
        assertThat(results.size()).isEqualTo(3);

        MetricAggregations aggregationResults = metricRepository.aggregateByFlowId(
            tenant,
            "namespace",
            "flow",
            null,
            counter.getName(),
            now.minusDays(30),
            now,
            "sum"
        );

        // The exact bucket count at the range boundary is backend-dependent: JDBC fills
        // the half-open range [start, end) in fixed 1-unit steps (30 buckets), whereas
        // Elasticsearch uses a calendar_interval date_histogram with inclusive extended
        // bounds [floor(start), floor(end)] (31 buckets). Both are valid groupings, so we
        // accept either; the meaningful assertion is that grouping is by day.
        assertThat(aggregationResults.getAggregations().size()).isBetween(30, 31);
        assertThat(aggregationResults.getGroupBy()).isEqualTo("day");

        aggregationResults = metricRepository.aggregateByFlowId(
            tenant,
            "namespace",
            "flow",
            null,
            counter.getName(),
            now.minusWeeks(26),
            now,
            "sum"
        );

        // Same backend-dependent boundary as above: JDBC yields 26 weekly buckets,
        // Elasticsearch's calendar-aligned histogram yields 27.
        assertThat(aggregationResults.getAggregations().size()).isBetween(26, 27);
        assertThat(aggregationResults.getGroupBy()).isEqualTo("week");

    }

    @Test
    void names() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();
        TaskRun taskRun1 = taskRun(tenant, executionId, "task");
        MetricEntry counter = MetricEntry.of(taskRun1, counter("counter"), null);

        TaskRun taskRun2 = taskRun(tenant, executionId, "task2");
        MetricEntry counter2 = MetricEntry.of(taskRun2, counter("counter2"), null);

        MetricEntry test = MetricEntry.of(taskRun2, counter("test"), ExecutionKind.TEST);

        metricRepository.save(counter);
        metricRepository.save(counter2);
        metricRepository.save(test); // should only be retrieved by execution id

        List<String> flowMetricsNames = metricRepository.flowMetrics(tenant, "namespace", "flow");
        List<String> taskMetricsNames = metricRepository.taskMetrics(tenant, "namespace", "flow", "task");
        List<String> tasksWithMetrics = metricRepository.tasksWithMetrics(tenant, "namespace", "flow");

        assertThat(flowMetricsNames.size()).isEqualTo(2);
        assertThat(taskMetricsNames.size()).isEqualTo(1);
        assertThat(tasksWithMetrics.size()).isEqualTo(2);
    }

    @Test
    void findAllAsync() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();
        TaskRun taskRun1 = taskRun(tenant, executionId, "task");
        MetricEntry counter = MetricEntry.of(taskRun1, counter("counter"), null);
        TaskRun taskRun2 = taskRun(tenant, executionId, "task");
        MetricEntry timer = MetricEntry.of(taskRun2, timer(), null);
        MetricEntry test = MetricEntry.of(taskRun2, counter("test"), ExecutionKind.TEST);
        metricRepository.save(counter);
        metricRepository.save(timer);
        metricRepository.save(test); // should be retrieved as findAllAsync is used for backup

        List<MetricEntry> results = metricRepository.findAllAsync(tenant).collectList().block();
        assertThat(results).hasSize(3);
    }

    @Test
    void purge() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        metricRepository.save(MetricEntry.of(taskRun(tenant, "execution1", "task"), counter("counter1"), null));
        metricRepository.save(MetricEntry.of(taskRun(tenant, "execution1", "task"), counter("counter2"), null));
        metricRepository.save(MetricEntry.of(taskRun(tenant, "execution2", "task"), counter("counter1"), null));
        metricRepository.save(MetricEntry.of(taskRun(tenant, "execution2", "task"), counter("counter2"), null));

        var result = metricRepository.purge(List.of(Execution.builder().id("execution1").build(), Execution.builder().id("execution2").build()));
        assertThat(result).isEqualTo(4);
    }

    @Test
    protected void fetchData() throws IOException {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();
        TaskRun taskRun1 = taskRun(tenant, executionId, "task");
        MetricEntry counter = MetricEntry.of(taskRun1, counter("counter"), null);
        MetricEntry testCounter = MetricEntry.of(taskRun1, counter("test"), ExecutionKind.TEST);
        metricRepository.save(counter);
        metricRepository.save(testCounter);

        var results = metricRepository.fetchData(
            tenant,
            Metrics.builder().type(Metrics.class.getName()).columns(
                Map.of(
                    "count", ColumnDescriptor.<Metrics.Fields> builder().field(Metrics.Fields.EXECUTION_ID).agg(AggregationType.COUNT).build()
                )
            ).build(),
            null,
            null,
            Pageable.UNPAGED
        );

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().get("count")).isIn(1, 1L); // JDBC return an int but ES a long
    }

    @Test
    protected void fetchValue() throws IOException {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();
        TaskRun taskRun1 = taskRun(tenant, executionId, "task");
        MetricEntry counter = MetricEntry.of(taskRun1, counter("counter"), null);
        MetricEntry testCounter = MetricEntry.of(taskRun1, counter("test"), ExecutionKind.TEST);
        metricRepository.save(counter);
        metricRepository.save(testCounter);

        var results = metricRepository.fetchValue(
            tenant,
            MetricsKPI.builder().type(MetricsKPI.class.getName()).columns(ColumnDescriptor.<Metrics.Fields> builder().field(Metrics.Fields.EXECUTION_ID).agg(AggregationType.COUNT).build())
                .build(),
            null,
            null,
            false
        );

        assertThat(results).isEqualTo(1.0);
    }

    @Test
    void shouldPersistTaskIdLongerThan150Chars() {
        // A plugin-generated taskId (e.g. Ansible "<host> | <play> : <task>") can exceed the legacy
        // VARCHAR(150) task_id column and crash-loop the indexer; the column now allows up to 256.
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();
        String longTaskId = "a".repeat(200);
        MetricEntry counter = MetricEntry.of(taskRun(tenant, executionId, longTaskId), counter("counter"), null);

        metricRepository.save(counter);

        List<MetricEntry> results = metricRepository.findByExecutionIdAndTaskId(tenant, executionId, longTaskId, Pageable.from(1, 10));
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.getFirst().getTaskId()).isEqualTo(longTaskId);
    }

    private Counter counter(String metricName) {
        return Counter.of(metricName, 1);
    }

    private Timer timer() {
        return Timer.of("counter", Duration.ofSeconds(5));
    }

    private TaskRun taskRun(String tenantId, String executionId, String taskId) {
        return TaskRun.builder()
            .tenantId(tenantId)
            .flowId("flow")
            .namespace("namespace")
            .executionId(executionId)
            .taskId(taskId)
            .id(FriendlyId.createFriendlyId())
            .build();
    }

    @Test
    void shouldAggregateByFlowIdWithTaskIdFilter() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();

        TaskRun taskRunA = taskRun(tenant, executionId, "taskA");
        TaskRun taskRunB = taskRun(tenant, executionId, "taskB");

        metricRepository.save(MetricEntry.of(taskRunA, counter("shared-metric"), null));
        metricRepository.save(MetricEntry.of(taskRunB, counter("shared-metric"), null));

        MetricAggregations allTasks = metricRepository.aggregateByFlowId(
            tenant, "namespace", "flow", null, "shared-metric",
            ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), "sum"
        );

        MetricAggregations taskAOnly = metricRepository.aggregateByFlowId(
            tenant, "namespace", "flow", "taskA", "shared-metric",
            ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), "sum"
        );

        assertThat(allTasks.getAggregations()).isNotEmpty();
        assertThat(taskAOnly.getAggregations()).isNotEmpty();
    }

    @Test
    void shouldAggregateByFlowIdWithDifferentAggregationTypes() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();
        TaskRun taskRun1 = taskRun(tenant, executionId, "task");

        metricRepository.save(MetricEntry.of(taskRun1, counter("agg-metric"), null));

        for (String aggType : List.of("sum", "avg", "min", "max")) {
            MetricAggregations result = metricRepository.aggregateByFlowId(
                tenant, "namespace", "flow", null, "agg-metric",
                ZonedDateTime.now().minusDays(1), ZonedDateTime.now(), aggType
            );

            assertThat(result.getAggregations()).as("aggregation type: " + aggType).isNotEmpty();
        }
    }

    @Test
    void shouldPaginateFindByExecutionId() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();
        TaskRun taskRun1 = taskRun(tenant, executionId, "task");

        for (int i = 0; i < 5; i++) {
            metricRepository.save(MetricEntry.of(taskRun1, counter("metric" + i), null));
        }

        ArrayListTotal<MetricEntry> page1 = metricRepository.findByExecutionId(tenant, executionId, Pageable.from(1, 2));
        assertThat(page1).hasSize(2);
        assertThat(page1.getTotal()).isEqualTo(5);

        ArrayListTotal<MetricEntry> page3 = metricRepository.findByExecutionId(tenant, executionId, Pageable.from(3, 2));
        assertThat(page3).hasSize(1);
        assertThat(page3.getTotal()).isEqualTo(5);
    }

    @Test
    void shouldReturnDistinctTasksWithMetrics() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();

        TaskRun taskRunA = taskRun(tenant, executionId, "taskA");
        TaskRun taskRunB = taskRun(tenant, executionId, "taskB");

        metricRepository.save(MetricEntry.of(taskRunA, counter("m1"), null));
        metricRepository.save(MetricEntry.of(taskRunA, counter("m2"), null));
        metricRepository.save(MetricEntry.of(taskRunB, counter("m3"), null));

        List<String> tasksWithMetrics = metricRepository.tasksWithMetrics(tenant, "namespace", "flow");

        assertThat(tasksWithMetrics).containsExactlyInAnyOrder("taskA", "taskB");
    }

}
