package io.kestra.executor.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.services.TaskOutputService;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.ExecutorMessageHandler;
import io.kestra.executor.ExecutorService;
import io.kestra.executor.KillSwitchActionService;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class WorkerTaskResultMessageHandler implements ExecutorMessageHandler<WorkerTaskResult> {
    private final ExecutionStateStore executionStateStore;

    private final ExecutorService executorService;

    private final FlowMetaStoreInterface flowMetaStore;

    private final TaskOutputService taskOutputService;

    private final KillSwitchService killSwitchService;
    private final KillSwitchActionService killSwitchActionService;

    private final List<WorkerTaskResultListener> workerTaskResultListeners;

    @Inject
    public WorkerTaskResultMessageHandler(
        ExecutionStateStore executionStateStore,
        ExecutorService executorService,
        FlowMetaStoreInterface flowMetaStore,
        TaskOutputService taskOutputService,
        KillSwitchService killSwitchService,
        KillSwitchActionService killSwitchActionService,
        List<WorkerTaskResultListener> workerTaskResultListeners) {
        this.executionStateStore = executionStateStore;
        this.executorService = executorService;
        this.flowMetaStore = flowMetaStore;
        this.taskOutputService = taskOutputService;
        this.killSwitchService = killSwitchService;
        this.killSwitchActionService = killSwitchActionService;
        this.workerTaskResultListeners = workerTaskResultListeners;
    }

    @Override
    public Optional<ExecutorContext> handle(WorkerTaskResult message) {
        EvaluationType evaluationType = killSwitchService.evaluate(message.getTaskRun());
        if (evaluationType != EvaluationType.PASS) {
            var execution = executionStateStore.findById(message.getTaskRun().getExecutionId());
            if (execution != null && evaluationType.isKillSwitched(execution)) {
                killSwitchActionService.handle(evaluationType, execution.getTenantId(), execution.getId());
                return Optional.empty();
            }
        }

        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        List<JoinedWorkerTaskResult> joinedResults = new ArrayList<>();
        Optional<ExecutorContext> result = executionStateStore.lock(message.getTaskRun().getExecutionId(), execution ->
        {
            ExecutorContext current = new ExecutorContext(execution);

            try {
                for (WorkerTaskResult.WorkerTaskResultPayload precedingResult : message.getPrecedingResults()) {
                    joinIfPossible(current, execution, precedingResult.toWorkerTaskResult(), joinedResults);
                }
                joinIfPossible(current, execution, message, joinedResults);
                return joinedResults.isEmpty() ? null : current;
            } catch (InternalException e) {
                ExecutorContext failed = executorService.handleFailedExecutionFromExecutor(current, e);
                updateLastJoinedExecution(joinedResults, failed.getExecution());
                return failed;
            } catch (FlowNotFoundException e) {
                if (!current.getExecution().getState().getCurrent().isFailed()) {
                    ExecutorContext failed = executorService.handleFailedExecutionFromExecutor(current, e);
                    updateLastJoinedExecution(joinedResults, failed.getExecution());
                    return failed;
                }
                return null;
            }
        });

        if (result.isPresent()) {
            joinedResults.forEach(joined -> notifyJoined(joined.result(), joined.execution()));
        }

        return result;
    }

    private void joinIfPossible(
        ExecutorContext current,
        Execution execution,
        WorkerTaskResult message,
        List<JoinedWorkerTaskResult> joinedResults
    ) throws InternalException, FlowNotFoundException {
        if (current.getExecution().hasTaskRunJoinable(message.getTaskRun())) {
            int joinedResultIndex = joinedResults.size();
            joinedResults.add(new JoinedWorkerTaskResult(message, current.getExecution()));
            executorService.addWorkerTaskResult(
                current,
                () -> flowMetaStore.findByExecutionForRuntime(execution).orElseThrow(() -> new FlowNotFoundException(execution)),
                message
            );
            joinedResults.set(joinedResultIndex, new JoinedWorkerTaskResult(message, current.getExecution()));
        } else if (
            message.getOutputs() != null &&
                !message.getOutputs().isEmpty() &&
                !taskOutputService.hasOutputs(message.getTaskRun())
        ) {
            taskOutputService.saveOutputs(message.getTaskRun(), message.getOutputs());
        }
    }

    private void updateLastJoinedExecution(List<JoinedWorkerTaskResult> joinedResults, Execution execution) {
        if (!joinedResults.isEmpty()) {
            int lastIndex = joinedResults.size() - 1;
            joinedResults.set(lastIndex, new JoinedWorkerTaskResult(joinedResults.get(lastIndex).result(), execution));
        }
    }

    private void notifyJoined(WorkerTaskResult message, Execution execution) {
        for (WorkerTaskResultListener listener : workerTaskResultListeners) {
            try {
                listener.onJoined(message, execution);
            } catch (Exception e) {
                log.error("Worker task result listener {} failed", listener.getClass().getName(), e);
            }
        }
    }

    private record JoinedWorkerTaskResult(WorkerTaskResult result, Execution execution) {
    }
}
