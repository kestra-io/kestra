package io.kestra.executor.testkit;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import io.kestra.core.models.executions.TaskRun;
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
            .as(described("worker task for task <%s> (emitted: %s)", taskId, workerTaskIds()))
            .anyMatch(workerTask -> taskId.equals(workerTask.workerTask().getTaskRun().getTaskId()));
        return this;
    }

    public ExecutorContextAssert hasNoWorkerTasks() {
        isNotNull();
        Assertions.assertThat(actual.getWorkerTasks())
            .as(described("no worker tasks expected (emitted: %s)", workerTaskIds()))
            .isEmpty();
        return this;
    }

    public ExecutorContextAssert hasNoNexts() {
        isNotNull();
        Assertions.assertThat(actual.getNextCount()).as(described("no follow-up cycle requested")).isZero();
        return this;
    }

    /** The worker tasks emitted by this cycle, by task id, in emission order — and nothing else. */
    public ExecutorContextAssert hasWorkerTasksExactly(String... taskIds) {
        isNotNull();
        Assertions.assertThat(workerTaskIds()).as(described("worker tasks emitted this cycle")).containsExactly(taskIds);
        return this;
    }

    /** A single {@link ExecutionDelay} of the given type, targeting {@code targetState}, for this execution. */
    public ExecutorContextAssert hasExecutionDelay(ExecutionDelay.DelayType type, State.Type targetState) {
        isNotNull();
        Assertions.assertThat(actual.getExecutionDelays()).as(described("exactly one execution delay")).hasSize(1);
        ExecutionDelay delay = actual.getExecutionDelays().getFirst();
        Assertions.assertThat(delay.getDelayType()).as(described("delay type")).isEqualTo(type);
        Assertions.assertThat(delay.getState()).as(described("state the delay resumes into")).isEqualTo(targetState);
        Assertions.assertThat(delay.getExecutionId()).as(described("delay targets this execution")).isEqualTo(actual.getExecution().getId());
        return this;
    }

    /** The single delay's date is at or after {@code instant} — a lower bound, never an exact wall-clock date. */
    public ExecutorContextAssert hasExecutionDelayNotBefore(Instant instant) {
        isNotNull();
        Assertions.assertThat(actual.getExecutionDelays()).as(described("exactly one execution delay")).hasSize(1);
        Assertions.assertThat(actual.getExecutionDelays().getFirst().getDate()).as(described("delay date lower bound")).isAfterOrEqualTo(instant);
        return this;
    }

    public ExecutorContextAssert hasNoTaskRunFor(String taskId) {
        isNotNull();
        Assertions.assertThat(actual.getExecution().findTaskRunsByTaskId(taskId)).as(described("no taskrun yet for task <%s>", taskId)).isEmpty();
        return this;
    }

    /** The whole taskrun list by task id, in order — the saga's traversal order. */
    public ExecutorContextAssert hasTaskRunsExactly(String... taskIds) {
        isNotNull();
        Assertions.assertThat(actual.getExecution().getTaskRunList()).extracting(TaskRun::getTaskId).as(described("taskruns in order")).containsExactly(taskIds);
        return this;
    }

    /** The admission gate stamped the claimed concurrency scopes on the execution. */
    public ExecutorContextAssert hasClaimStamp() {
        isNotNull();
        Assertions.assertThat(actual.getExecution().getMetadata().getConcurrencyScopes()).as(described("concurrency claim stamp")).isNotEmpty();
        return this;
    }

    /** The state history starts at {@code first} — e.g. CREATED for an execution failed straight out of the gate. */
    public ExecutorContextAssert hasFirstState(State.Type first) {
        isNotNull();
        Assertions.assertThat(actual.getExecution().getState().getHistories().getFirst().getState()).as(described("first state in history")).isEqualTo(first);
        return this;
    }

    public ExecutorContextAssert hasNoExecutionDelays() {
        isNotNull();
        Assertions.assertThat(actual.getExecutionDelays()).as(described("no execution delay expected")).isEmpty();
        return this;
    }

    public ExecutorContextAssert hasSingleExecutionDelay(Consumer<ExecutionDelay> requirements) {
        isNotNull();
        Assertions.assertThat(actual.getExecutionDelays()).hasSize(1);
        requirements.accept(actual.getExecutionDelays().getFirst());
        return this;
    }

    public ExecutorContextAssert hasSubflowExecutions(int expected) {
        isNotNull();
        Assertions.assertThat(actual.getSubflowExecutions()).as(described("subflow executions requested this cycle")).hasSize(expected);
        return this;
    }

    public ExecutorContextAssert hasNoTaskRuns() {
        isNotNull();
        Assertions.assertThat(actual.getExecution().getTaskRunList()).as(described("no taskrun expected")).isNullOrEmpty();
        return this;
    }

    public ExecutorContextAssert hasNoSubflowExecutions() {
        isNotNull();
        Assertions.assertThat(actual.getSubflowExecutions()).as(described("no subflow execution expected")).isEmpty();
        return this;
    }

    public ExecutorContextAssert hasTaskRunInState(String taskId, State.Type state) {
        isNotNull();
        Assertions.assertThat(actual.getExecution().getTaskRunList())
            .as(described("taskrun for task <%s> in state <%s>", taskId, state))
            .anyMatch(taskRun -> taskId.equals(taskRun.getTaskId()) && taskRun.getState().getCurrent() == state);
        return this;
    }

    public ExecutorContextAssert executionInState(State.Type state) {
        isNotNull();
        Assertions.assertThat(actual.getExecution().getState().getCurrent())
            .as(described("execution state (cycle contributed by %s)", actual.getFrom()))
            .isEqualTo(state);
        return this;
    }

    /**
     * Asserts against the distinct state types this execution passed through within the cycle.
     */
    public ExecutorContextAssert transitioned(State.Type... states) {
        isNotNull();
        Assertions.assertThat(actual.getStateTransitions()).as(described("states passed through this cycle")).containsExactly(states);
        return this;
    }

    /**
     * Asserts that the given handler contributed to this cycle (the {@code from} audit trail).
     */
    public ExecutorContextAssert updatedFrom(String from) {
        isNotNull();
        Assertions.assertThat(actual.getFrom()).as(described("handlers that contributed to this cycle")).contains(from);
        return this;
    }

    /** Prefixes the test's own {@code as(...)} description, so a failure reads "<why> — <what>". */
    private String described(String detail, Object... args) {
        String what = detail.formatted(args);
        return descriptionText().isEmpty() ? what : descriptionText() + " — " + what;
    }

    private List<String> workerTaskIds() {
        return actual.getWorkerTasks().stream()
            .map(workerTask -> workerTask.workerTask().getTaskRun().getTaskId())
            .toList();
    }
}
