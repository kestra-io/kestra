package io.kestra.core.models.executions;

import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.plugin.core.log.Log;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionGuessFinalStateTest {

    @Test
    void guessFinalState_shouldReturnSuccessWhenAllTasksSucceed() {
        // Create an execution with a successful task
        TaskRun successTaskRun = TaskRun.builder()
            .id("task1")
            .taskId("task1")
            .executionId("exec1")
            .state(new State().withState(State.Type.SUCCESS))
            .build();

        Execution execution = Execution.builder()
            .id("exec1")
            .namespace("test")
            .flowId("flow1")
            .flowRevision(1)
            .taskRunList(List.of(successTaskRun))
            .state(new State().withState(State.Type.RUNNING))
            .build();

        State.Type finalState = execution.guessFinalState(Collections.emptyList(), null, false, false);
        assertThat(finalState).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void guessFinalState_shouldReturnSuccessWhenTaskSucceedsAfterRetry() {
        // Create an execution with a task that succeeded after retries
        // This simulates the scenario where a task initially failed but succeeded on retry
        // The key insight: we only look at the final state of the TaskRun, not intermediate attempts
        TaskRun taskWithRetrySuccess = TaskRun.builder()
            .id("task1")
            .taskId("task1")
            .executionId("exec1")
            .state(new State().withState(State.Type.SUCCESS)) // Final state is SUCCESS after retries
            .attempts(List.of(
                // Failed attempts
                TaskRunAttempt.builder().state(new State().withState(State.Type.FAILED)).build(),
                TaskRunAttempt.builder().state(new State().withState(State.Type.FAILED)).build(),
                // Final successful attempt
                TaskRunAttempt.builder().state(new State().withState(State.Type.SUCCESS)).build()
            ))
            .build();

        Execution execution = Execution.builder()
            .id("exec1")
            .namespace("test")
            .flowId("flow1")
            .flowRevision(1)
            .taskRunList(List.of(taskWithRetrySuccess))
            .state(new State().withState(State.Type.RUNNING))
            .build();

        Log logTask = Log.builder().id("task1").message("test").build();
        List<ResolvedTask> allTasks = ResolvedTask.of(List.of(logTask));
        State.Type finalState = execution.guessFinalState(allTasks, null, false, false);
        assertThat(finalState).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void guessFinalState_shouldReturnFailedWhenTaskFails() {
        // Create an execution with a failed task
        TaskRun failedTaskRun = TaskRun.builder()
            .id("task1")
            .taskId("task1")
            .executionId("exec1")
            .state(new State().withState(State.Type.FAILED))
            .build();

        Execution execution = Execution.builder()
            .id("exec1")
            .namespace("test")
            .flowId("flow1")
            .flowRevision(1)
            .taskRunList(List.of(failedTaskRun))
            .state(new State().withState(State.Type.RUNNING))
            .build();

        // Use all task runs for the final state calculation
        Log logTask = Log.builder().id("task1").message("test").build();
        List<ResolvedTask> allTasks = ResolvedTask.of(List.of(logTask));
        State.Type finalState = execution.guessFinalState(allTasks, null, false, false);
        assertThat(finalState).isEqualTo(State.Type.FAILED);
    }

    @Test
    void guessFinalState_shouldReturnFailedWhenMixedTasksWithFailure() {
        // Create an execution with multiple tasks where one succeeds but another fails
        // This tests that if any task truly fails (not just retries), the execution should fail
        TaskRun successTaskRun = TaskRun.builder()
            .id("task1")
            .taskId("task1")
            .executionId("exec1")
            .state(new State().withState(State.Type.SUCCESS))
            .build();

        TaskRun failedTaskRun = TaskRun.builder()
            .id("task2")
            .taskId("task2")
            .executionId("exec1")
            .state(new State().withState(State.Type.FAILED)) // This task ultimately failed
            .build();

        Execution execution = Execution.builder()
            .id("exec1")
            .namespace("test")
            .flowId("flow1")
            .flowRevision(1)
            .taskRunList(List.of(successTaskRun, failedTaskRun))
            .state(new State().withState(State.Type.RUNNING))
            .build();

        Log logTask1 = Log.builder().id("task1").message("test1").build();
        Log logTask2 = Log.builder().id("task2").message("test2").build();
        List<ResolvedTask> allTasks = ResolvedTask.of(List.of(logTask1, logTask2));
        State.Type finalState = execution.guessFinalState(allTasks, null, false, false);
        assertThat(finalState).isEqualTo(State.Type.FAILED);
    }

    @Test
    void guessFinalState_shouldReturnSuccessWhenMixedTasksWithRetriesSucceed() {
        // Create an execution where multiple subflows succeeded after retries
        // This tests the original issue scenario: both subflows eventually succeed after retries
        TaskRun subflowInbox = TaskRun.builder()
            .id("subflow_inbox")
            .taskId("subflow_inbox")
            .executionId("exec1")
            .state(new State().withState(State.Type.SUCCESS)) // Final state after retries
            .attempts(List.of(
                TaskRunAttempt.builder().state(new State().withState(State.Type.FAILED)).build(),
                TaskRunAttempt.builder().state(new State().withState(State.Type.FAILED)).build(),
                TaskRunAttempt.builder().state(new State().withState(State.Type.SUCCESS)).build()
            ))
            .build();

        TaskRun subflowSent = TaskRun.builder()
            .id("subflow_sent")
            .taskId("subflow_sent")
            .executionId("exec1")
            .state(new State().withState(State.Type.SUCCESS)) // Final state after retries
            .attempts(List.of(
                TaskRunAttempt.builder().state(new State().withState(State.Type.FAILED)).build(),
                TaskRunAttempt.builder().state(new State().withState(State.Type.FAILED)).build(),
                TaskRunAttempt.builder().state(new State().withState(State.Type.SUCCESS)).build()
            ))
            .build();

        Execution execution = Execution.builder()
            .id("exec1")
            .namespace("test")
            .flowId("flow1")
            .flowRevision(1)
            .taskRunList(List.of(subflowInbox, subflowSent))
            .state(new State().withState(State.Type.RUNNING))
            .build();

        Log logTask1 = Log.builder().id("subflow_inbox").message("inbox").build();
        Log logTask2 = Log.builder().id("subflow_sent").message("sent").build();
        List<ResolvedTask> allTasks = ResolvedTask.of(List.of(logTask1, logTask2));
        State.Type finalState = execution.guessFinalState(allTasks, null, false, false);
        assertThat(finalState).isEqualTo(State.Type.SUCCESS);
    }
}