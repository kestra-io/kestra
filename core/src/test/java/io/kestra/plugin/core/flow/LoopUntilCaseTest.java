package io.kestra.plugin.core.flow;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.services.TaskOutputService;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class LoopUntilCaseTest {

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    private TaskOutputService taskOutputService;

    public void waitfor(String tenantId) throws TimeoutException, QueueException, io.kestra.core.exceptions.InternalException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().getFirst())).isNotNull();
        assertThat((Integer) taskOutputService.getOutputs(execution.getTaskRunList().getFirst()).get("iterationCount")).isEqualTo(1);
    }

    public void waitforMaxIterations(String tenantId) throws TimeoutException, QueueException, io.kestra.core.exceptions.InternalException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor-max-iterations");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().getFirst())).isNotNull();
        assertThat((Integer) taskOutputService.getOutputs(execution.getTaskRunList().getFirst()).get("iterationCount")).isEqualTo(4);
    }

    public void waitforMaxDuration(String tenantId) throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor-max-duration");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        TaskRun parentTaskRun = execution.findTaskRunsByTaskId("waitfor").getFirst();
        State.History creationHistory = parentTaskRun.getState().getHistories().getFirst();
        State.History failureHistory = parentTaskRun.getState().getHistories().getLast();
        assertThat(creationHistory.getDate().plus(5, ChronoUnit.SECONDS).isBefore(failureHistory.getDate()));
    }

    public void waitforNoSuccess(String tenantId) throws TimeoutException, QueueException, io.kestra.core.exceptions.InternalException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor-no-success");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().getFirst())).isNotNull();
        assertThat((Integer) taskOutputService.getOutputs(execution.getTaskRunList().getFirst()).get("iterationCount")).isEqualTo(5);
    }

    @SuppressWarnings("unchecked")
    public void waitforMultipleTasks(String tenantId) throws TimeoutException, QueueException, io.kestra.core.exceptions.InternalException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor-multiple-tasks");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().getFirst())).isNotNull();
        assertThat((Integer) taskOutputService.getOutputs(execution.getTaskRunList().getFirst()).get("iterationCount")).isEqualTo(3);
        Map<String, Object> values = (Map<String, Object>) taskOutputService.getOutputs(execution.getTaskRunList().getLast()).get("values");
        assertThat(values.get("count")).isEqualTo("4");
    }

    public void waitforMultipleTasksFailed(String tenantId) throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor-multiple-tasks-failed");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getLast().attemptNumber()).isEqualTo(1);
    }

    public void waitForChildTaskWarning(String tenantId) throws TimeoutException, QueueException, io.kestra.core.exceptions.InternalException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor-child-task-warning");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat((Integer) taskOutputService.getOutputs(execution.getTaskRunList().getFirst()).get("iterationCount")).isGreaterThan(1);
    }
}
