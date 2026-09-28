package io.kestra.plugin.core.flow;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.core.runners.RunContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Nullable;

/**
 * A flowable task whose task run is resumed from a PAUSED state, and that can collect inputs at resume time.
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
     * Defaults to RUNNING, letting the flowable resolve its own state from its children.
     */
    default State.Type resumedTaskRunState(State.Type newState) {
        return State.Type.RUNNING;
    }

    /**
     * A scheduled auto-resume for {@code taskRun}, once it reaches PAUSED, or empty when it should stay
     * paused until manually resumed.
     */
    Optional<ExecutionDelay> pauseDelay(TaskRun taskRun, RunContext runContext) throws IllegalVariableEvaluationException, InternalException;

    /**
     * Whether the task run is still preparing its pause (e.g. Approval's {@code onWait}), keeping the execution PAUSING rather than PAUSED.
     * Only asked of a RUNNING task run that has never been PAUSED.
     */
    default boolean isPausing(RunContext runContext, TaskRun taskRun) {
        return false;
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
