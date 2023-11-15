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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ExecutableUtils {

    private ExecutableUtils() {
        // prevent initialization
    }

    public static State.Type guessState(Execution execution, boolean transmitFailed, State.Type defaultState) {
        if (transmitFailed &&
            (execution.getState().isFailed() || execution.getState().isPaused() || execution.getState().getCurrent() == State.Type.KILLED || execution.getState().getCurrent() == State.Type.WARNING)
        ) {
            return execution.getState().getCurrent();
        } else {
            return defaultState;
        }
    }

    public static WorkerTaskResult workerTaskResult(TaskRun taskRun) {
        return WorkerTaskResult.builder()
            .taskRun(taskRun.withAttempts(
                Collections.singletonList(TaskRunAttempt.builder().state(new State().withState(taskRun.getState().getCurrent())).build())
            ))
            .build();
    }

    public static <T extends Task & ExecutableTask<?>> WorkerTaskExecution<?> workerTaskExecution(
        RunContext runContext,
        FlowExecutorInterface flowExecutorInterface,
        Execution currentExecution,
        Flow currentFlow,
        T currentTask,
        TaskRun currentTaskRun,
        Map<String, Object> inputs,
        List<Label> labels,
        Integer iteration
    ) throws IllegalVariableEvaluationException {
        String subflowNamespace = runContext.render(currentTask.subflowId().namespace());
        String subflowId = runContext.render(currentTask.subflowId().flowId());
        Optional<Integer> subflowRevision = currentTask.subflowId().revision();

        io.kestra.core.models.flows.Flow flow = flowExecutorInterface.findByIdFromFlowTask(
                currentExecution.getTenantId(),
                subflowNamespace,
                subflowId,
                subflowRevision,
                currentExecution.getTenantId(),
                currentFlow.getNamespace(),
                currentFlow.getId()
            )
            .orElseThrow(() -> new IllegalStateException("Unable to find flow '" + subflowNamespace + "'.'" + subflowId + "' with revision + '" + subflowRevision.orElse(0) + "'"));

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

        RunnerUtils runnerUtils = runContext.getApplicationContext().getBean(RunnerUtils.class);
        Execution execution = runnerUtils
            .newExecution(
                flow,
                (f, e) -> runnerUtils.typedInputs(f, e, inputs),
                labels)
            .withTrigger(ExecutionTrigger.builder()
                .id(currentTask.getId())
                .type(currentTask.getType())
                .variables(variables)
                .build()
            );

        return WorkerTaskExecution.builder()
            .task(currentTask)
            .taskRun(currentTaskRun)
            .execution(execution)
            .iteration(iteration)
            .build();
    }

    public static TaskRun manageIterations(TaskRun taskRun, Execution execution, boolean transmitFailed) throws InternalException {
        if (taskRun.getOutputs() != null && taskRun.getOutputs().containsKey("iterations")) {
            Map<String, Integer> taskRunIteration = (Map<String, Integer>) taskRun.getOutputs().get("iterations");
            int maxIterations = taskRunIteration.get("max");

            var previousTaskRun = execution.findTaskRunByTaskRunId(taskRun.getId());
            if (previousTaskRun != null) {
                // search for the previous iteration, if not found, we init it with the current iteration
                Map<String, Integer> iterations = previousTaskRun.getOutputs() != null ? (Map<String, Integer>) previousTaskRun.getOutputs().get("iterations") : taskRunIteration;
                State.Type currentState = taskRun.getState().getCurrent();
                Optional<State.Type> previousState = taskRun.getState().getHistories().size() > 1 ? Optional.of(taskRun.getState().getHistories().get(taskRun.getState().getHistories().size() - 2).getState()) : Optional.empty();

                int currentStateIteration = getIterationCounter(iterations, currentState, maxIterations) + 1;
                iterations.put(currentState.toString(), currentStateIteration);
                if (previousState.isPresent() && previousState.get() != currentState) {
                    int previousStateIterations = getIterationCounter(iterations, previousState.get(), maxIterations) - 1;
                    iterations.put(previousState.get().toString(), previousStateIterations);
                }

                // update the state to success if current == max
                int terminatedIterations =  iterations.getOrDefault(State.Type.SUCCESS.toString(), 0) +
                    iterations.getOrDefault(State.Type.FAILED.toString(), 0) +
                    iterations.getOrDefault(State.Type.KILLED.toString(), 0) +
                    iterations.getOrDefault(State.Type.WARNING.toString(), 0) +
                    iterations.getOrDefault(State.Type.CANCELLED.toString(), 0);
                if (terminatedIterations != maxIterations && taskRun.getState().isTerminated()) {
                    // there will be n terminated task runs, but we should only set it to terminated for the last one
                    // the final state should be computed based on the iterations
                    return previousTaskRun.withOutputs(Map.of("iterations", iterations));
                } else if (terminatedIterations == maxIterations && taskRun.getState().isTerminated()) {
                    var state = transmitFailed ? findTerminalState(iterations) : State.Type.SUCCESS;
                    return previousTaskRun.withOutputs(Map.of("iterations", iterations))
                        .withAttempts(Collections.singletonList(TaskRunAttempt.builder().state(new State().withState(state)).build()))
                        .withState(state);
                }
                return taskRun.withOutputs(Map.of("iterations", iterations));
            }
        }

        return taskRun;
    }

    private static State.Type findTerminalState(Map<String, Integer> iterations) {
        if (iterations.getOrDefault(State.Type.FAILED.toString(), 0) > 0) {
            return State.Type.FAILED;
        }
        if (iterations.getOrDefault(State.Type.KILLED.toString(), 0) > 0) {
            return State.Type.KILLED;
        }
        if (iterations.getOrDefault(State.Type.WARNING.toString(), 0) > 0) {
            return State.Type.WARNING;
        }
        return State.Type.SUCCESS;
    }

    private static int getIterationCounter(Map<String, Integer> iterations, State.Type state, int maxIterations) {
        // if the state is created and there is no existing counter, it means it's the first time we iterate so we init created to the number of iterations
        return iterations.getOrDefault(state.toString(), state == State.Type.CREATED ? maxIterations : 0);
    }
}
