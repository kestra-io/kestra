package io.kestra.executor.testkit;

import java.util.List;
import java.util.function.Consumer;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.executor.ExecutorContext;

/**
 * AssertJ vocabulary over the {@link ExecutorContext} command object. Assertions match on task
 * ids, states and delay types — never on generated ids or wall-clock state-history timestamps.
 */
public class ExecutorContextAssert extends AbstractAssert<ExecutorContextAssert, ExecutorContext> {

    private ExecutorContextAssert(ExecutorContext actual) {
        super(actual, ExecutorContextAssert.class);
    }

    public static ExecutorContextAssert assertThat(ExecutorContext actual) {
        return new ExecutorContextAssert(actual);
    }

    public ExecutorContextAssert hasWorkerTaskFor(String taskId) {
        isNotNull();
        Assertions.assertThat(actual.getWorkerTasks())
            .as("worker task for task <%s> (emitted: %s)", taskId, workerTaskIds())
            .anyMatch(workerTask -> taskId.equals(workerTask.workerTask().getTaskRun().getTaskId()));
        return this;
    }

    public ExecutorContextAssert hasNoWorkerTasks() {
        isNotNull();
        Assertions.assertThat(actual.getWorkerTasks())
            .as("no worker tasks expected (emitted: %s)", workerTaskIds())
            .isEmpty();
        return this;
    }

    public ExecutorContextAssert hasNoNexts() {
        isNotNull();
        Assertions.assertThat(actual.getNextCount()).isZero();
        return this;
    }

    public ExecutorContextAssert hasNoExecutionDelays() {
        isNotNull();
        Assertions.assertThat(actual.getExecutionDelays()).isEmpty();
        return this;
    }

    public ExecutorContextAssert hasSingleExecutionDelay(Consumer<ExecutionDelay> requirements) {
        isNotNull();
        Assertions.assertThat(actual.getExecutionDelays()).hasSize(1);
        requirements.accept(actual.getExecutionDelays().getFirst());
        return this;
    }

    public ExecutorContextAssert hasNoSubflowExecutions() {
        isNotNull();
        Assertions.assertThat(actual.getSubflowExecutions()).isEmpty();
        return this;
    }

    public ExecutorContextAssert hasTaskRunInState(String taskId, State.Type state) {
        isNotNull();
        Assertions.assertThat(actual.getExecution().getTaskRunList())
            .as("taskrun for task <%s> in state <%s>", taskId, state)
            .anyMatch(taskRun -> taskId.equals(taskRun.getTaskId()) && taskRun.getState().getCurrent() == state);
        return this;
    }

    public ExecutorContextAssert executionInState(State.Type state) {
        isNotNull();
        Assertions.assertThat(actual.getExecution().getState().getCurrent()).isEqualTo(state);
        return this;
    }

    /**
     * Asserts against the distinct state types this execution passed through within the cycle.
     */
    public ExecutorContextAssert transitioned(State.Type... states) {
        isNotNull();
        Assertions.assertThat(actual.getStateTransitions()).containsExactly(states);
        return this;
    }

    /**
     * Asserts that the given handler contributed to this cycle (the {@code from} audit trail).
     */
    public ExecutorContextAssert updatedFrom(String from) {
        isNotNull();
        Assertions.assertThat(actual.getFrom()).contains(from);
        return this;
    }

    private List<String> workerTaskIds() {
        return actual.getWorkerTasks().stream()
            .map(workerTask -> workerTask.workerTask().getTaskRun().getTaskId())
            .toList();
    }
}
