package io.kestra.plugin.core.flow;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.core.runners.RunContext;

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
    Map<String, Object> resumeOutputs(Map<String, Object> inputs, Pause.Resumed resumed, @Nullable Approval.Decision decision);

    /**
     * The state this task run itself should take once the execution resumes to {@code newState}.
     */
    State.Type resumedTaskRunState(State.Type newState);

    /**
     * A scheduled auto-resume for {@code taskRun}, once it reaches PAUSED, or empty when it should stay
     * paused until manually resumed.
     */
    Optional<ExecutionDelay> pauseDelay(TaskRun taskRun, RunContext runContext) throws IllegalVariableEvaluationException, InternalException;
}
