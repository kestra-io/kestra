package io.kestra.core.repositories;

import com.devskiller.friendly_id.FriendlyId;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.MetricAggregations;
import io.kestra.core.models.executions.metrics.Timer;
import io.micronaut.data.model.Pageable;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public abstract class AbstractMetricRepositoryTest {
    @Inject
    protected MetricRepositoryInterface metricRepository;

    @Test
    void all() {
        String executionId = FriendlyId.createFriendlyId();
        TaskRun taskRun1 = taskRun(executionId, "task");
        MetricEntry counter = MetricEntry.of(taskRun1, counter("counter"));
        TaskRun taskRun2 = taskRun(executionId, "task");
        MetricEntry timer = MetricEntry.of(taskRun2, timer());
        metricRepository.save(counter);
        metricRepository.save(timer);

        List<MetricEntry> results = metricRepository.findByExecutionId(null, executionId, Pageable.from(1, 10));
        assertThat(results.size(), is(2));

        results = metricRepository.findByExecutionIdAndTaskId(null, executionId, taskRun1.getTaskId(), Pageable.from(1, 10));
        assertThat(results.size(), is(2));

        results = metricRepository.findByExecutionIdAndTaskRunId(null, executionId, taskRun1.getId(), Pageable.from(1, 10));
        assertThat(results.size(), is(1));

        MetricAggregations aggregationResults = metricRepository.aggregateByFlowId(
            null,
            "namespace",
            "flow",
            null,
            counter.getName(),
            ZonedDateTime.now().minusDays(30),
            ZonedDateTime.now(),
            "sum"
        );

        assertThat(aggregationResults.getAggregations().size(), is(31));
        assertThat(aggregationResults.getGroupBy(), is("day"));

        aggregationResults = metricRepository.aggregateByFlowId(
            null,
            "namespace",
            "flow",
            null,
            counter.getName(),
            ZonedDateTime.now().minusWeeks(26),
            ZonedDateTime.now(),
            "sum"
        );

        assertThat(aggregationResults.getAggregations().size(), is(27));
        assertThat(aggregationResults.getGroupBy(), is("week"));

    }

     @Test
     void names() {
         String executionId = FriendlyId.createFriendlyId();
         TaskRun taskRun1 = taskRun(executionId, "task");
         MetricEntry counter = MetricEntry.of(taskRun1, counter("counter"));

         TaskRun taskRun2 = taskRun(executionId, "task2");
         MetricEntry counter2 = MetricEntry.of(taskRun2, counter("counter2"));

         metricRepository.save(counter);
         metricRepository.save(counter2);


         List<String> flowMetricsNames = metricRepository.flowMetrics(null, "namespace", "flow");
         List<String> taskMetricsNames = metricRepository.taskMetrics(null, "namespace", "flow", "task");
         List<String> tasksWithMetrics = metricRepository.tasksWithMetrics(null, "namespace", "flow");

         assertThat(flowMetricsNames.size(), is(2));
         assertThat(taskMetricsNames.size(), is(1));
         assertThat(tasksWithMetrics.size(), is(2));
     }

    private Counter counter(String metricName) {
        return Counter.of(metricName, 1);
    }

    private Timer timer() {
        return Timer.of("counter", Duration.ofSeconds(5));
    }

    private TaskRun taskRun(String executionId, String taskId) {
        return TaskRun.builder()
            .flowId("flow")
            .namespace("namespace")
            .executionId(executionId)
            .taskId(taskId)
            .id(FriendlyId.createFriendlyId())
            .build();
    }
}
