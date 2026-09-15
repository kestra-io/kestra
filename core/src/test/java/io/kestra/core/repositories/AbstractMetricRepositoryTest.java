package io.kestra.core.repositories;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
import io.micronaut.data.model.Sort;
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

        // A window over 365 days groups by month; the bucket containing endDate must still be
        // emitted, otherwise the most recent data point is silently zero-filled away (regression
        // for JDBC backends walking un-floored buckets from startDate). endDate is captured fresh
        // here (not the `now` above, which predates the save) so it is guaranteed to be at or
        // after the counter's own timestamp.
        ZonedDateTime monthlyEnd = ZonedDateTime.now();
        aggregationResults = metricRepository.aggregateByFlowId(
            tenant,
            "namespace",
            "flow",
            null,
            counter.getName(),
            monthlyEnd.minusDays(400),
            monthlyEnd,
            "sum"
        );

        // The `timer` entry above reuses the metric name "counter" too, reporting its duration in
        // milliseconds, so the expected sum is the counter's value plus the timer's millisecond value.
        double expectedMonthlySum = 1.0 + timer.getValue();
        assertThat(aggregationResults.getGroupBy()).isEqualTo("month");
        assertThat(aggregationResults.getAggregations().stream().mapToDouble(bucket -> bucket.value).sum()).isEqualTo(expectedMonthlySum);

        // avg/min/max must aggregate over a window with mostly empty buckets without failing
        // (regression for the Elasticsearch backend returning a null value for empty buckets).
        for (String aggregation : List.of("avg", "min", "max")) {
            MetricAggregations aggregations = metricRepository.aggregateByFlowId(
                tenant,
                "namespace",
                "flow",
                null,
                counter.getName(),
                ZonedDateTime.now().minusDays(30),
                ZonedDateTime.now(),
                aggregation
            );

            assertThat(aggregations.getAggregations().size()).isEqualTo(31);
            assertThat(aggregations.getGroupBy()).isEqualTo("day");
        }
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
    void shouldPurgeMetricsBeforeEndDate() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant oldTimestamp = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant recentTimestamp = Instant.now();

        metricRepository.save(MetricEntry.builder()
            .tenantId(tenant)
            .namespace("io.kestra.purge")
            .flowId("flow1")
            .executionId("exec1")
            .taskId("task1")
            .taskRunId(FriendlyId.createFriendlyId())
            .type("counter")
            .name("metric1")
            .value(1.0)
            .timestamp(oldTimestamp)
            .build());

        metricRepository.save(MetricEntry.builder()
            .tenantId(tenant)
            .namespace("io.kestra.purge")
            .flowId("flow1")
            .executionId("exec2")
            .taskId("task1")
            .taskRunId(FriendlyId.createFriendlyId())
            .type("counter")
            .name("metric2")
            .value(2.0)
            .timestamp(recentTimestamp)
            .build());

        int deleted = metricRepository.purge(tenant, "io.kestra.purge", "flow1", null, ZonedDateTime.now().minusDays(5));
        assertThat(deleted).isEqualTo(1);

        List<MetricEntry> remaining = metricRepository.findByExecutionId(tenant, "exec2", Pageable.from(1, 10));
        assertThat(remaining).hasSize(1);

        List<MetricEntry> purged = metricRepository.findByExecutionId(tenant, "exec1", Pageable.from(1, 10));
        assertThat(purged).isEmpty();
    }

    @Test
    void shouldPurgeMetricsWithNamespacePrefix() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant timestamp = Instant.now().minus(10, ChronoUnit.DAYS);

        metricRepository.save(MetricEntry.builder()
            .tenantId(tenant)
            .namespace("company.team")
            .flowId("flow1")
            .executionId("exec1")
            .taskId("task1")
            .taskRunId(FriendlyId.createFriendlyId())
            .type("counter")
            .name("metric1")
            .value(1.0)
            .timestamp(timestamp)
            .build());

        metricRepository.save(MetricEntry.builder()
            .tenantId(tenant)
            .namespace("company.team.sub")
            .flowId("flow2")
            .executionId("exec2")
            .taskId("task1")
            .taskRunId(FriendlyId.createFriendlyId())
            .type("counter")
            .name("metric2")
            .value(2.0)
            .timestamp(timestamp)
            .build());

        metricRepository.save(MetricEntry.builder()
            .tenantId(tenant)
            .namespace("other.team")
            .flowId("flow3")
            .executionId("exec3")
            .taskId("task1")
            .taskRunId(FriendlyId.createFriendlyId())
            .type("counter")
            .name("metric3")
            .value(3.0)
            .timestamp(timestamp)
            .build());

        int deleted = metricRepository.purge(tenant, "company.team", null, null, ZonedDateTime.now());
        assertThat(deleted).isEqualTo(2);

        List<MetricEntry> other = metricRepository.findByExecutionId(tenant, "exec3", Pageable.from(1, 10));
        assertThat(other).hasSize(1);
    }

    @Test
    void shouldPurgeMetricsWithStartDateAndEndDate() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant t1 = Instant.now().minus(15, ChronoUnit.DAYS);
        Instant t2 = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant t3 = Instant.now().minus(2, ChronoUnit.DAYS);

        metricRepository.save(MetricEntry.builder()
            .tenantId(tenant)
            .namespace("ns")
            .flowId("flow")
            .executionId("exec1")
            .taskId("task")
            .taskRunId(FriendlyId.createFriendlyId())
            .type("counter")
            .name("m1")
            .value(1.0)
            .timestamp(t1)
            .build());

        metricRepository.save(MetricEntry.builder()
            .tenantId(tenant)
            .namespace("ns")
            .flowId("flow")
            .executionId("exec2")
            .taskId("task")
            .taskRunId(FriendlyId.createFriendlyId())
            .type("counter")
            .name("m2")
            .value(2.0)
            .timestamp(t2)
            .build());

        metricRepository.save(MetricEntry.builder()
            .tenantId(tenant)
            .namespace("ns")
            .flowId("flow")
            .executionId("exec3")
            .taskId("task")
            .taskRunId(FriendlyId.createFriendlyId())
            .type("counter")
            .name("m3")
            .value(3.0)
            .timestamp(t3)
            .build());

        int deleted = metricRepository.purge(tenant, "ns", "flow", ZonedDateTime.now().minusDays(12), ZonedDateTime.now().minusDays(5));
        assertThat(deleted).isEqualTo(1);

        assertThat(metricRepository.findByExecutionId(tenant, "exec1", Pageable.from(1, 10))).hasSize(1);
        assertThat(metricRepository.findByExecutionId(tenant, "exec2", Pageable.from(1, 10))).isEmpty();
        assertThat(metricRepository.findByExecutionId(tenant, "exec3", Pageable.from(1, 10))).hasSize(1);
    }

    @Test
    void shouldRespectTenantIsolationWhenPurgingMetrics() {
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Instant oldTimestamp = Instant.now().minus(10, ChronoUnit.DAYS);

        metricRepository.save(MetricEntry.builder()
            .tenantId(tenant1)
            .namespace("ns")
            .flowId("flow")
            .executionId("exec1")
            .taskId("task")
            .taskRunId(FriendlyId.createFriendlyId())
            .type("counter")
            .name("m1")
            .value(1.0)
            .timestamp(oldTimestamp)
            .build());

        metricRepository.save(MetricEntry.builder()
            .tenantId(tenant2)
            .namespace("ns")
            .flowId("flow")
            .executionId("exec2")
            .taskId("task")
            .taskRunId(FriendlyId.createFriendlyId())
            .type("counter")
            .name("m2")
            .value(2.0)
            .timestamp(oldTimestamp)
            .build());

        int deleted = metricRepository.purge(tenant1, null, null, null, ZonedDateTime.now());
        assertThat(deleted).isEqualTo(1);

        assertThat(metricRepository.findByExecutionId(tenant1, "exec1", Pageable.from(1, 10))).isEmpty();
        assertThat(metricRepository.findByExecutionId(tenant2, "exec2", Pageable.from(1, 10))).hasSize(1);
    }

    @Test
    void shouldReturnZeroWhenNoMatchingMetricsToPurge() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        int deleted = metricRepository.purge(tenant, "non.existing.ns", null, null, ZonedDateTime.now());
        assertThat(deleted).isEqualTo(0);
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

    @Test
    void shouldSortByValueUnlikeOtherResourcesWhereItIsExcluded() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();
        TaskRun taskRun1 = taskRun(tenant, executionId, "task");

        metricRepository.save(MetricEntry.of(taskRun1, Counter.of("c", 3), null));
        metricRepository.save(MetricEntry.of(taskRun1, Counter.of("c", 1), null));
        metricRepository.save(MetricEntry.of(taskRun1, Counter.of("c", 2), null));

        Function<String, String> sortMapper = metricRepository.sortMapping();
        Pageable pageable = Pageable.from(1, 10, Sort.of(Sort.Order.asc(sortMapper.apply("value"))));

        List<MetricEntry> results = metricRepository.findByExecutionId(tenant, executionId, pageable);

        assertThat(results).extracting(MetricEntry::getValue).containsExactly(1.0, 2.0, 3.0);
    }

    @Test
    void shouldSortByTaskRunIdUsingItsApiFieldName() {
        // Regression: AbstractJdbcMetricRepository.sortMapping() used to map the lowercase "taskrunId",
        // rejecting the camelCase "taskRunId" that MetricEntry#getTaskRunId() and every other sort key
        // in this map actually use.
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();
        TaskRun taskRun1 = taskRun(tenant, executionId, "task");
        metricRepository.save(MetricEntry.of(taskRun1, counter("c"), null));

        Function<String, String> sortMapper = metricRepository.sortMapping();
        String mapped = sortMapper.apply("taskRunId");
        assertThat(mapped).isNotNull();

        Pageable pageable = Pageable.from(1, 10, Sort.of(Sort.Order.asc(mapped)));
        List<MetricEntry> results = metricRepository.findByExecutionId(tenant, executionId, pageable);

        assertThat(results).hasSize(1);
    }

}
