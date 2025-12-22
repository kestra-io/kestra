package io.kestra.core.repositories;

import com.devskiller.friendly_id.FriendlyId;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.MetricAggregations;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public abstract class AbstractMetricRepositoryTest {
    @Inject
    protected MetricRepositoryInterface metricRepository;

    @Test
    void all() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = FriendlyId.createFriendlyId();
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
            ZonedDateTime.now().minusDays(30),
            ZonedDateTime.now(),
            "sum"
        );

        assertThat(aggregationResults.getAggregations().size()).isEqualTo(31);
        assertThat(aggregationResults.getGroupBy()).isEqualTo("day");

        aggregationResults = metricRepository.aggregateByFlowId(
            tenant,
            "namespace",
            "flow",
            null,
            counter.getName(),
            ZonedDateTime.now().minusWeeks(26),
            ZonedDateTime.now(),
            "sum"
        );

        assertThat(aggregationResults.getAggregations().size()).isEqualTo(27);
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
}
