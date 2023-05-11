package io.kestra.core.repositories;

import com.devskiller.friendly_id.FriendlyId;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.MetricAggregations;
import io.kestra.core.models.executions.metrics.Timer;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest(transactional = false)
public abstract class AbstractMetricRepositoryTest {
    @Inject
    protected MetricRepositoryInterface metricRepository;

    @Test
    void all() {
        String executionId = FriendlyId.createFriendlyId();
        TaskRun taskRun1 = taskRun(executionId);
        MetricEntry counter = MetricEntry.of(taskRun1, counter());
        TaskRun taskRun2 = taskRun(executionId);
        MetricEntry timer = MetricEntry.of(taskRun2, timer());
        metricRepository.save(counter);
        metricRepository.save(timer);

        List<MetricEntry> results = metricRepository.findByExecutionId(executionId, Pageable.from(1, 10));
        assertThat(results.size(), is(2));

        results = metricRepository.findByExecutionIdAndTaskId(executionId, taskRun1.getTaskId(), Pageable.from(1, 10));
        assertThat(results.size(), is(2));

        results = metricRepository.findByExecutionIdAndTaskRunId(executionId, taskRun1.getId(), Pageable.from(1, 10));
        assertThat(results.size(), is(1));

        MetricAggregations aggregationResults = metricRepository.aggregateByFlowId(
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
            "namespace",
            "flow",
            null,
            counter.getName(),
            ZonedDateTime.now().minusDays(190),
            ZonedDateTime.now(),
            "sum"
        );

        assertThat(aggregationResults.getAggregations().size(), is(28));
        assertThat(aggregationResults.getGroupBy(), is("week"));

    }

    private Counter counter() {
        return Counter.of("counter", 1);
    }

    private Timer timer() {
        return Timer.of("counter", Duration.ofSeconds(5));
    }

    private TaskRun taskRun(String executionId) {
        return TaskRun.builder()
            .flowId("flow")
            .namespace("namespace")
            .executionId(executionId)
            .taskId("task")
            .id(FriendlyId.createFriendlyId())
            .build();
    }
}
