package io.kestra.core.runners;

import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
public class TaskWithAllowFailureTest {
    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @ExecuteFlow("flows/valids/task-allow-failure-runnable.yml")
    void runnableTask(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.findTaskRunsByTaskId("fail").getFirst().getAttempts().size()).isEqualTo(3);
    }

    @Test
    @LoadFlows(
        value = { "flows/valids/task-allow-failure-executable-flow.yml",
            "flows/valids/failing-subflow.yaml" },
        tenantId = "tenant1"
    )
    void executableTask_Flow() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne("tenant1", "io.kestra.tests", "task-allow-failure-executable-flow");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(execution.getTaskRunList()).hasSize(2);
    }

    @Test
    @ExecuteFlow("flows/valids/task-allow-failure-flowable.yml")
    void flowableTask(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(execution.getTaskRunList()).hasSize(3);
    }
}
