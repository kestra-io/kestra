package io.kestra.executor.handler;

import java.util.List;
import java.util.Optional;

import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.WorkerTaskResult;
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

    private final KillSwitchService killSwitchService;
    private final KillSwitchActionService killSwitchActionService;

    private final List<WorkerTaskResultListener> workerTaskResultListeners;

    @Inject
    public WorkerTaskResultMessageHandler(
        ExecutionStateStore executionStateStore,
        ExecutorService executorService,
        FlowMetaStoreInterface flowMetaStore,
        KillSwitchService killSwitchService,
        KillSwitchActionService killSwitchActionService,
        List<WorkerTaskResultListener> workerTaskResultListeners) {
        this.executionStateStore = executionStateStore;
        this.executorService = executorService;
        this.flowMetaStore = flowMetaStore;
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

        Optional<ExecutorContext> result = executionStateStore.lock(message.getTaskRun().getExecutionId(), execution ->
        {
            ExecutorContext current = new ExecutorContext(execution);

            if (execution.hasTaskRunJoinable(message.getTaskRun())) {
                try {
                    // process worker task result
                    executorService.addWorkerTaskResult(
                        current,
                        () -> flowMetaStore.findByExecutionThenInjectDefaults(execution).orElseThrow(() -> new FlowNotFoundException(execution)),
                        message
                    );
                    // join worker result
                    return current;
                } catch (InternalException e) {
                    return executorService.handleFailedExecutionFromExecutor(current, e);
                } catch (FlowNotFoundException e) {
                    // avoid infinite for FlowNotFoundException
                    if (!current.getExecution().getState().getCurrent().isFailed()) {
                        return executorService.handleFailedExecutionFromExecutor(current, e);
                    }
                }
            }

            return null;
        });

        // a present result means the taskrun was joined here (redeliveries come back empty): notify listeners once
        if (result.isPresent()) {
            notifyJoined(message, result.get().getExecution());
        }

        return result;
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
}
