package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ExecutableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.storages.Storage;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public final class ExecutableUtils {

    public static final String TASK_VARIABLE_ITERATIONS = "iterations";
    public static final String TASK_VARIABLE_NUMBER_OF_BATCHES = "numberOfBatches";
    public static final String TASK_VARIABLE_SUBFLOW_OUTPUTS_BASE_URI = "subflowOutputsBaseUri";

    private ExecutableUtils() {
        // prevent initialization
    }

    public static State.Type guessState(Execution execution, boolean transmitFailed, boolean allowedFailure) {
        if (transmitFailed &&
            (execution.getState().isFailed() || execution.getState().isPaused() || execution.getState().getCurrent() == State.Type.KILLED || execution.getState().getCurrent() == State.Type.WARNING)
        ) {
            return (allowedFailure && execution.getState().isFailed()) ? State.Type.WARNING : execution.getState().getCurrent();
        } else {
            return State.Type.SUCCESS;
        }
    }

    public static SubflowExecutionResult subflowExecutionResult(TaskRun parentTaskrun, Execution execution) {
        return SubflowExecutionResult.builder()
            .executionId(execution.getId())
            .state(parentTaskrun.getState().getCurrent())
            .parentTaskRun(parentTaskrun.withAttempts(
                Collections.singletonList(TaskRunAttempt.builder().state(new State().withState(parentTaskrun.getState().getCurrent())).build())
            ))
            .build();
    }

    public static <T extends Task & ExecutableTask<?>> SubflowExecution<?> subflowExecution(
        RunContext runContext,
        FlowExecutorInterface flowExecutorInterface,
        Execution currentExecution,
        Flow currentFlow,
        T currentTask,
        TaskRun currentTaskRun,
        Map<String, Object> inputs,
        List<Label> labels
    ) throws IllegalVariableEvaluationException {
        String subflowNamespace = runContext.render(currentTask.subflowId().namespace());
        String subflowId = runContext.render(currentTask.subflowId().flowId());
        Optional<Integer> subflowRevision = currentTask.subflowId().revision();

        io.kestra.core.models.flows.Flow flow = flowExecutorInterface.findByIdFromTask(
                currentExecution.getTenantId(),
                subflowNamespace,
                subflowId,
                subflowRevision,
                currentExecution.getTenantId(),
                currentFlow.getNamespace(),
                currentFlow.getId()
            )
            .orElseThrow(() -> new IllegalStateException("Unable to find flow '" + subflowNamespace + "'.'" + subflowId + "' with revision '" + subflowRevision.orElse(0) + "'"));

        if (flow.isDisabled()) {
            throw new IllegalStateException("Cannot execute a flow which is disabled");
        }

        if (flow instanceof FlowWithException fwe) {
            throw new IllegalStateException("Cannot execute an invalid flow: " + fwe.getException());
        }

        Map<String, Object> variables = ImmutableMap.of(
            "executionId", currentExecution.getId(),
            "namespace", currentFlow.getNamespace(),
            "flowId", currentFlow.getId(),
            "flowRevision", currentFlow.getRevision()
        );

        FlowInputOutput flowInputOutput = runContext.getApplicationContext().getBean(FlowInputOutput.class);
        Execution execution = Execution
            .newExecution(
                flow,
                (f, e) -> flowInputOutput.typedInputs(f, e, inputs),
                labels)
            .withTrigger(ExecutionTrigger.builder()
                .id(currentTask.getId())
                .type(currentTask.getType())
                .variables(variables)
                .build()
            );

        return SubflowExecution.builder()
            .parentTask(currentTask)
            .parentTaskRun(currentTaskRun.withState(State.Type.RUNNING))
            .execution(execution)
            .build();
    }

    @SuppressWarnings("unchecked")
    public static TaskRun manageIterations(Storage storage, TaskRun taskRun, Execution execution, boolean transmitFailed, boolean allowFailure) throws InternalException {
        Integer numberOfBatches = (Integer) taskRun.getOutputs().get(TASK_VARIABLE_NUMBER_OF_BATCHES);
        var previousTaskRun = execution.findTaskRunByTaskRunId(taskRun.getId());
        if (previousTaskRun == null) {
            throw new IllegalStateException("Should never happen");
        }

        State.Type currentState = taskRun.getState().getCurrent();
        Optional<State.Type> previousState = taskRun.getState().getHistories().size() > 1 ?
            Optional.of(taskRun.getState().getHistories().get(taskRun.getState().getHistories().size() - 2).getState()) :
            Optional.empty();

        // search for the previous iterations, if not found, we init it with an empty map
        Map<String, Integer> iterations = previousTaskRun.getOutputs() != null ?
            (Map<String, Integer>) previousTaskRun.getOutputs().get(TASK_VARIABLE_ITERATIONS) :
            new HashMap<>();

        int currentStateIteration = iterations.getOrDefault(currentState.toString(), 0);
        iterations.put(currentState.toString(), currentStateIteration + 1);
        if (previousState.isPresent() && previousState.get() != currentState) {
            int previousStateIterations = iterations.getOrDefault(previousState.get().toString(), numberOfBatches);
            iterations.put(previousState.get().toString(), previousStateIterations - 1);
        }

        // update the state to success if terminatedIterations == numberOfBatches
        int terminatedIterations = iterations.getOrDefault(State.Type.SUCCESS.toString(), 0) +
            iterations.getOrDefault(State.Type.FAILED.toString(), 0) +
            iterations.getOrDefault(State.Type.KILLED.toString(), 0) +
            iterations.getOrDefault(State.Type.WARNING.toString(), 0) +
            iterations.getOrDefault(State.Type.CANCELLED.toString(), 0);

        if (terminatedIterations == numberOfBatches) {
            State.Type state = transmitFailed ? findTerminalState(iterations, allowFailure) : State.Type.SUCCESS;
            final Map<String, Object> outputs = new HashMap<>();
            outputs.put(TASK_VARIABLE_ITERATIONS, iterations);
            outputs.put(TASK_VARIABLE_NUMBER_OF_BATCHES, numberOfBatches);
            outputs.put(TASK_VARIABLE_SUBFLOW_OUTPUTS_BASE_URI, storage.getContextBaseURI().getPath());

            return previousTaskRun
                .withIteration(taskRun.getIteration())
                .withOutputs(outputs)
                .withAttempts(Collections.singletonList(TaskRunAttempt.builder().state(new State().withState(state)).build()))
                .withState(state);
        }

        // else we update the previous taskRun as it's the same taskRun that is still running
        return previousTaskRun
            .withIteration(taskRun.getIteration())
            .withOutputs(Map.of(
                TASK_VARIABLE_ITERATIONS, iterations,
                TASK_VARIABLE_NUMBER_OF_BATCHES, numberOfBatches
            ));
    }

    private static State.Type findTerminalState(Map<String, Integer> iterations, boolean allowFailure) {
        if (iterations.getOrDefault(State.Type.FAILED.toString(), 0) > 0) {
            return allowFailure ? State.Type.WARNING : State.Type.FAILED;
        }
        if (iterations.getOrDefault(State.Type.KILLED.toString(), 0) > 0) {
            return State.Type.KILLED;
        }
        if (iterations.getOrDefault(State.Type.WARNING.toString(), 0) > 0) {
            return State.Type.WARNING;
        }
        return State.Type.SUCCESS;
    }
}
