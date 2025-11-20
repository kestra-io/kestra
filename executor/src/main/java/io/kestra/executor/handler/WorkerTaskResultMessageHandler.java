package io.kestra.executor.handler;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.ExecutorService;
import io.kestra.executor.ExecutorMessageHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Singleton
@Slf4j
public class WorkerTaskResultMessageHandler implements ExecutorMessageHandler<WorkerTaskResult> {
    @Inject
    private ExecutionStateStore executionStateStore;

    @Inject
    private ExecutorService executorService;

    @Inject
    private FlowMetaStoreInterface flowMetaStore;

    @Override
    public Optional<ExecutorContext> handle(WorkerTaskResult message) {
        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        return executionStateStore.lock(message.getTaskRun().getExecutionId(), execution -> {
            ExecutorContext current = new ExecutorContext(execution);

            if (execution.hasTaskRunJoinable(message.getTaskRun())) {
                try {
                    // process worker task result
                    executorService.addWorkerTaskResult(current, () -> flowMetaStore.findByExecutionThenInjectDefaults(execution).orElseThrow(), message);
                    // join worker result
                    return current;
                } catch (InternalException e) {
                    return executorService.handleFailedExecutionFromExecutor(current, e);
                }
            }

            return null;
        });
    }
}
