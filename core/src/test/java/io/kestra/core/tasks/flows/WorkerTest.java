package io.kestra.core.tasks.flows;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.runners.RunnerUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class WorkerTest extends AbstractMemoryRunnerTest {
    @Inject
    Suite suite;

    @Test
    void success() throws TimeoutException {
       suite.success(runnerUtils);
    }

    @Test
    void failed() throws TimeoutException {
        suite.failed(runnerUtils);
    }

    @Test
    void each() throws TimeoutException {
        suite.each(runnerUtils);
    }

    @Singleton
    public static class Suite {
        public void success(RunnerUtils runnerUtils) throws TimeoutException {
            Execution execution = runnerUtils.runOne("io.kestra.tests", "worker", null,
                (f, e) -> ImmutableMap.of("failed", "false"), Duration.ofSeconds(60)
            );

            assertThat(execution.getTaskRunList(), hasSize(4));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertThat(execution.getTaskRunList().get(3).getOutputs().get("value"), is(execution.getTaskRunList().get(1).getId()));
        }

        public void failed(RunnerUtils runnerUtils) throws TimeoutException {
            Execution execution = runnerUtils.runOne("io.kestra.tests", "worker", null,
                (f, e) -> ImmutableMap.of("failed", "true"), Duration.ofSeconds(60)
            );

            assertThat(execution.getTaskRunList(), hasSize(3));
            assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
            assertThat(execution.findTaskRunsByTaskId("error-t1"), hasSize(1));
        }

        public void each(RunnerUtils runnerUtils) throws TimeoutException {
            Execution execution = runnerUtils.runOne("io.kestra.tests", "worker-each", Duration.ofSeconds(60));

            assertThat(execution.getTaskRunList(), hasSize(8));
            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
            assertThat(execution.findTaskRunsByTaskId("2_end").get(0).getOutputs().get("value"), is(execution.findTaskRunsByTaskId("first").get(0).getId()));
        }
    }
}
