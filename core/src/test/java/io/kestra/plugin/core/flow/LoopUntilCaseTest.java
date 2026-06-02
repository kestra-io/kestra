package io.kestra.plugin.core.flow;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.TestRunnerUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

public class LoopUntilCaseTest {

    @Inject
    protected TestRunnerUtils runnerUtils;

    public void waitfor(String tenantId) throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().getFirst().getOutputs()).isNotNull();
        assertThat((Integer) execution.getTaskRunList().getFirst().getOutputs().get("iterationCount")).isEqualTo(1);
    }

    public void waitforMaxIterations(String tenantId) throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor-max-iterations");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getFirst().getOutputs()).isNotNull();
        assertThat((Integer) execution.getTaskRunList().getFirst().getOutputs().get("iterationCount")).isEqualTo(4);
    }

    public void waitforMaxDuration(String tenantId) throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor-max-duration");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        TaskRun parentTaskRun = execution.findTaskRunsByTaskId("waitfor").getFirst();
        State.History creationHistory = parentTaskRun.getState().getHistories().getFirst();
        State.History failureHistory = parentTaskRun.getState().getHistories().getLast();
        assertThat(creationHistory.getDate().plus(5, ChronoUnit.SECONDS).isBefore(failureHistory.getDate()));
    }

    public void waitforNoSuccess(String tenantId) throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor-no-success");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList().getFirst().getOutputs()).isNotNull();
        assertThat((Integer) execution.getTaskRunList().getFirst().getOutputs().get("iterationCount")).isEqualTo(5);
    }

    @SuppressWarnings("unchecked")
    public void waitforMultipleTasks(String tenantId) throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor-multiple-tasks");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertThat(execution.getTaskRunList().getFirst().getOutputs()).isNotNull();
        assertThat((Integer) execution.getTaskRunList().getFirst().getOutputs().get("iterationCount")).isEqualTo(3);
        Map<String, Object> values = (Map<String, Object>) execution.getTaskRunList().getLast().getOutputs().get("values");
        assertThat(values.get("count")).isEqualTo("4");
    }

    public void waitforMultipleTasksFailed(String tenantId) throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor-multiple-tasks-failed");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getLast().attemptNumber()).isEqualTo(1);
    }

    public void waitForChildTaskWarning(String tenantId) throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor-child-task-warning");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat((Integer) execution.getTaskRunList().getFirst().getOutputs().get("iterationCount")).isGreaterThan(1);
    }

    public void waitforNestedThreeLevels(String tenantId) throws TimeoutException, QueueException, io.kestra.core.exceptions.InternalException {
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "waitfor-nested", Duration.ofSeconds(60));

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        // Outer loop ran all 3 iterations
        TaskRun loop1 = execution.findTaskRunsByTaskId("loop_1").getFirst();
        // The `iteration` field of the last loop_3_log encodes how deeply the outer loops reset.
        // With the bug (stale outputs not cleaned), loop_3 exits after 1 inner iteration giving iteration=3.
        // With the fix (all descendants removed on outer-loop reset), loop_3 runs 3 inner iterations each
        // time giving iteration=6, confirming the inner loops properly restarted from scratch.
        TaskRun lastLoop3Log = execution.findTaskRunsByTaskId("loop_3_log").getFirst();
        assertThat(lastLoop3Log.getIteration()).isEqualTo(6);
    }
}
