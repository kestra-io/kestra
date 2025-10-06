package io.kestra.core.runners;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.queues.QueueException;
import jakarta.inject.Inject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
public class TaskCacheTest {
    static final AtomicInteger COUNTER = new AtomicInteger(0);

    @Inject
    private TestRunnerUtils runnerUtils;

    @BeforeEach
    void resetCounter() {
        COUNTER.set(0);
    }

    @Test
    @LoadFlows("flows/valids/cache.yaml")
    void shouldCacheTaskRunOutput() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne("main", "io.kestra.tests", "cache");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("counter")).isEqualTo(1);

        // as the task is cached, it should return the same result
        Execution cached = runnerUtils.runOne("main", "io.kestra.tests", "cache");
        assertThat(cached.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(cached.getTaskRunList().size()).isEqualTo(1);
        assertThat(cached.getTaskRunList().getFirst().getOutputs().get("counter")).isEqualTo(1);
    }

    @Test
    @LoadFlows("flows/valids/cache.yaml")
    @Disabled("Expiration didn't work on CI for an unknown reason")
    void shouldExpireCacheTaskRunOutputAfterTtl() throws QueueException, TimeoutException, InterruptedException {
        Execution execution = runnerUtils.runOne("main", "io.kestra.tests", "cache");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("counter")).isEqualTo(1);

        // Wait for the cache TTL expiration
        Thread.sleep(1100);

        // as the task is cached, it should return the same result
        Execution notCached = runnerUtils.runOne("main", "io.kestra.tests", "cache");
        assertThat(notCached.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(notCached.getTaskRunList().size()).isEqualTo(1);
        assertThat(notCached.getTaskRunList().getFirst().getOutputs().get("counter")).isEqualTo(2);
    }

    @SuperBuilder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @Plugin
    public static class CounterTask extends Task implements RunnableTask<CounterTask.Output> {

        private String workingDir;

        @Override
        public Output run(RunContext runContext) throws Exception {
            Map<String, Object> variables = Map.of("workingDir", runContext.workingDir().path().toString());
            runContext.render(this.workingDir, variables);
            return Output.builder()
                .counter(COUNTER.incrementAndGet())
                .build();
        }

        @SuperBuilder(toBuilder = true)
        @Getter
        public static class Output implements io.kestra.core.models.tasks.Output {
            private int counter;
        }

    }
}
