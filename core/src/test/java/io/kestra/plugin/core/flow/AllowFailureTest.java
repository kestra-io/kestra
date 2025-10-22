package io.kestra.plugin.core.flow;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.TestRunnerUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class AllowFailureTest {
    @Inject
    private FlowInputOutput flowIO;
    @Inject
    protected TestRunnerUtils runnerUtils;

    @Test
    @ExecuteFlow("flows/valids/allow-failure.yaml")
    void success(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(9);
        control(execution);
        assertThat(execution.findTaskRunsByTaskId("global-error").size()).isZero();
        assertThat(execution.findTaskRunsByTaskId("last").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
    }

    @Test
    @LoadFlows(value = {"flows/valids/allow-failure.yaml"}, tenantId = "fail")
    void failed() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            "fail",
            "io.kestra.tests",
            "allow-failure",
            null,
            (f, e) -> flowIO.readExecutionInputs(f, e, ImmutableMap.of("crash", "1"))
        );

        assertThat(execution.getTaskRunList()).hasSize(10);
        control(execution);
        assertThat(execution.findTaskRunsByTaskId("global-error").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("switch").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("crash").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @ExecuteFlow("flows/valids/allow-failure-with-retry.yaml")
    void withRetry(Execution execution) {
        // Verify the execution completes in warning
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);

        // Verify the retry_block completes with WARNING (because child task failed but was allowed)
        assertThat(execution.findTaskRunsByTaskId("retry_block").getFirst().getState().getCurrent()).isEqualTo(State.Type.WARNING);

        // Verify failing_task was retried (3 attempts total: initial + 2 retries)
        assertThat(execution.findTaskRunsByTaskId("failing_task").getFirst().attemptNumber()).isEqualTo(3);
        assertThat(execution.findTaskRunsByTaskId("failing_task").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // Verify error handler was executed on failures
        assertThat(execution.findTaskRunsByTaskId("error_handler").size()).isEqualTo(1);

        // Verify finally block executed
        assertThat(execution.findTaskRunsByTaskId("finally_task").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // Verify downstream_task executed (proving the flow didn't get stuck)
        assertThat(execution.findTaskRunsByTaskId("downstream_task").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    private static void control(Execution execution) {
        assertThat(execution.findTaskRunsByTaskId("first").getFirst().getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(execution.findTaskRunsByTaskId("1-1-allow-failure").getFirst().getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(execution.findTaskRunsByTaskId("1-1-1_seq").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("1-1-1-1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("local-error").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("1-2-todo").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }
}