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
        // Verify the execution completes successfully
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // Verify the retry_block completes with WARNING (because child task failed but was allowed)
        assertThat(execution.findTaskRunsByTaskId("retry_block").getFirst().getState().getCurrent()).isEqualTo(State.Type.WARNING);

        // Verify run_script task was retried and eventually succeeded
        assertThat(execution.findTaskRunsByTaskId("run_script").size()).isGreaterThan(1);
        assertThat(execution.findTaskRunsByTaskId("run_script").getLast().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // Verify error handler (incr_counter) was executed
        assertThat(execution.findTaskRunsByTaskId("incr_counter").size()).isGreaterThan(0);

        // Verify finally block executed
        assertThat(execution.findTaskRunsByTaskId("log_ok").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // Verify the_end task executed (proving the flow didn't get stuck)
        assertThat(execution.findTaskRunsByTaskId("the_end").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
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