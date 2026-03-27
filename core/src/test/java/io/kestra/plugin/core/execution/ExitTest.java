package io.kestra.plugin.core.execution;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class ExitTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    @ExecuteFlow("flows/valids/exit.yaml")
    void shouldExitTheExecution(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.WARNING);
    }

    @Test
    @ExecuteFlow("flows/valids/exit-killed.yaml")
    void shouldExitAndKillTheExecution(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(execution.getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.KILLED);

    }

    @Test
    @ExecuteFlow("flows/valids/exit-nested.yaml")
    void shouldExitAndFailNestedIf(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList()).hasSize(4);
        assertThat(execution.findTaskRunsByTaskId("if_some_bool").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("if_some_bool").getFirst().getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("nested_bool_check").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("nested_bool_check").getFirst().getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("nested_was_false").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }
}
