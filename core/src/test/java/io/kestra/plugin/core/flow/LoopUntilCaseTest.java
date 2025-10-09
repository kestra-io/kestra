package io.kestra.plugin.core.flow;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.TestRunnerUtils;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

public class LoopUntilCaseTest {

    @Inject
    protected TestRunnerUtils runnerUtils;

    public void waitfor() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "waitfor");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().getFirst().getOutputs()).isNotNull();
        assertThat((Integer) execution.getTaskRunList().getFirst().getOutputs().get("iterationCount")).isEqualTo(1);
    }

    public void waitforMaxIterations() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "waitfor-max-iterations");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getFirst().getOutputs()).isNotNull();
        assertThat((Integer) execution.getTaskRunList().getFirst().getOutputs().get("iterationCount")).isEqualTo(4);
    }

    public void waitforMaxDuration() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "waitfor-max-duration");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void waitforNoSuccess() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "waitfor-no-success");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().getFirst().getOutputs()).isNotNull();
        assertThat((Integer) execution.getTaskRunList().getFirst().getOutputs().get("iterationCount")).isEqualTo(5);
    }

    @SuppressWarnings("unchecked")
    public void waitforMultipleTasks() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "waitfor-multiple-tasks");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertThat(execution.getTaskRunList().getFirst().getOutputs()).isNotNull();
        assertThat((Integer) execution.getTaskRunList().getFirst().getOutputs().get("iterationCount")).isEqualTo(3);
        Map<String,Object> values = (Map<String, Object>) execution.getTaskRunList().getLast().getOutputs().get("values");
        assertThat(values.get("count")).isEqualTo("4");
    }

    public void waitforMultipleTasksFailed() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "waitfor-multiple-tasks-failed");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getLast().attemptNumber()).isEqualTo(1);
    }

    public void waitForChildTaskWarning() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "waitfor-child-task-warning");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat((Integer) execution.getTaskRunList().getFirst().getOutputs().get("iterationCount")).isGreaterThan(1);
    }
}
