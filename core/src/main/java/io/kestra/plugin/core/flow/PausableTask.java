package io.kestra.plugin.core.flow;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.core.runners.RunContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Nullable;

/**
 * A task whose task run is paused and resumed by the executor, and that can collect inputs at resume time.
 */
public interface PausableTask {
    /**
     * Inputs collected from the user before the task run resumes, in the same shape as flow inputs.
     */
    List<Input<?>> resumeInputs();

    /**
     * Builds the outputs to store on the task run once it is resumed.
     *
     * @param inputs the values collected against {@link #resumeInputs()}
     * @param resumed who resumed the execution, when, and to which state
     * @param decision the decision the execution was resumed with, or null when resumed without one (e.g. {@link Pause})
     */
    Map<String, Object> resumeOutputs(Map<String, Object> inputs, Resumed resumed, @Nullable Decision decision);

    /**
     * The state this task run itself should take once the execution resumes to {@code newState}.
     * An empty result lets the caller default to RUNNING, for tasks that resolve their state from children.
     */
    default Optional<State.Type> resumedTaskRunState(State.Type newState) {
        return Optional.empty();
    }

    /** Hooks dispatched by the executor when the task run pauses, without delaying the pause or its resume. */
    default List<Task> onPauseTasks() {
        return List.of();
    }

    /**
     * A scheduled auto-resume for {@code taskRun}, once it reaches PAUSED, or empty when it should stay
     * paused until manually resumed.
     */
    Optional<ExecutionDelay> pauseDelay(TaskRun taskRun, RunContext runContext) throws IllegalVariableEvaluationException, InternalException;

    /** Called on the executor once the task run is created, before it runs; returns outputs to merge onto it. Must be idempotent, since an executor restart can replay it. */
    default Map<String, Object> created(FlowInterface flow, Execution execution, TaskRun taskRun, RunContext runContext) throws Exception {
        return Map.of();
    }

    /** Outputs to merge onto the task run once it is PAUSED, after its onWait tasks ran; {@code delay} is what {@link #pauseDelay} scheduled, so outputs written from its date cannot drift from it. */
    default Map<String, Object> pausedOutputs(RunContext runContext, Optional<ExecutionDelay> delay) throws IllegalVariableEvaluationException {
        return Map.of();
    }

    /** The decision recorded when the pause delay fires, or empty to resume the task run to the delay's state instead. */
    default Optional<Decision> expiredDecision() {
        return Optional.empty();
    }

    /** The state the whole execution should end in now that this task run terminated, or empty to let the execution carry on. */
    default Optional<State.Type> executionLevelOutcome(RunContext runContext, Execution execution, TaskRun taskRun) throws IllegalVariableEvaluationException {
        return Optional.empty();
    }

    record Resumed(@Nullable String by, LocalDateTime on, State.Type to) {
        public static Resumed now() {
            return new Resumed(null, LocalDateTime.now(), State.Type.SUCCESS);
        }

        public static Resumed now(State.Type to) {
            return new Resumed(null, LocalDateTime.now(), to);
        }

        public static Resumed now(String by) {
            return new Resumed(by, LocalDateTime.now(), State.Type.SUCCESS);
        }

        public static Resumed now(String by, State.Type to) {
            return new Resumed(by, LocalDateTime.now(), to);
        }
    }

    record Decision(Type type, @Nullable String comment) {
        public enum Type {
            APPROVED,
            DENIED,
            EXPIRED
        }
    }
}
