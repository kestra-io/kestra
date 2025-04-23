package io.kestra.plugin.core.flow;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.RunnerUtils;
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
    protected RunnerUtils runnerUtils;

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
    @LoadFlows({"flows/valids/allow-failure.yaml"})
    void failed() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            null,
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