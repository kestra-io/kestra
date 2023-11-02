package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ExecutableUtils {

    private ExecutableUtils() {
        // prevent initialization
    }

    public static State.Type guessState(Execution execution, boolean transmitFailed) {
        if (transmitFailed &&
            (execution.getState().isFailed() || execution.getState().isPaused() || execution.getState().getCurrent() == State.Type.KILLED || execution.getState().getCurrent() == State.Type.WARNING)
        ) {
            return execution.getState().getCurrent();
        } else {
            return State.Type.SUCCESS;
        }
    }

    public static WorkerTaskResult workerTaskResult(TaskRun taskRun) {
        return WorkerTaskResult.builder()
            .taskRun(taskRun.withAttempts(
                Collections.singletonList(TaskRunAttempt.builder().state(new State().withState(taskRun.getState().getCurrent())).build())
            ))
            .build();
    }

    public static <T extends Task & ExecutableTask> WorkerTaskExecution<?> workerTaskExecution(
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
            .build();
    }
}
