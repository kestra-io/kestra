package io.kestra.executor.handler;

import java.util.Optional;

import io.kestra.core.exceptions.FlowNotFoundException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
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
    @Inject
    private ExecutionStateStore executionStateStore;

    @Inject
    private ExecutorService executorService;

    @Inject
    private FlowMetaStoreInterface flowMetaStore;

    @Inject
    private KillSwitchService killSwitchService;
    @Inject
    private KillSwitchActionService killSwitchActionService;

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

        return executionStateStore.lock(message.getTaskRun().getExecutionId(), execution ->
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
    }
}
