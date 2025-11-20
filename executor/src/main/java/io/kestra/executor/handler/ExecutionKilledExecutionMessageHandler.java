package io.kestra.executor.handler;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.ExecutorService;
import io.kestra.executor.ExecutorMessageHandler;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Singleton
@Slf4j
public class ExecutionKilledExecutionMessageHandler implements ExecutorMessageHandler<ExecutionKilledExecution> {
    @Inject
    private ExecutorService executorService;
    @Inject
    private ExecutionService executionService;

    @Inject
    private ExecutionStateStore executionStateStore;
    @Inject
    private ExecutionQueuedStateStore executionQueuedStateStore;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private FlowMetaStoreInterface flowMetaStore;

    @Inject
    @Named(QueueFactoryInterface.KILL_NAMED)
    private QueueInterface<ExecutionKilled> killQueue;

    @Override
    public Optional<ExecutorContext> handle(ExecutionKilledExecution message) {
        metricRegistry
            .counter(MetricRegistry.METRIC_EXECUTOR_KILLED_COUNT, MetricRegistry.METRIC_EXECUTOR_KILLED_COUNT_DESCRIPTION, metricRegistry.tags(message))
            .increment();

        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        // Immediately fire the event in EXECUTED state to notify the Workers to kill
        // any remaining tasks for that executing regardless of if the execution exist or not.
        // Note, that this event will be a noop if all tasks for that execution are already killed or completed.
        try {
            killQueue.emit(ExecutionKilledExecution
                .builder()
                .executionId(message.getExecutionId())
                .isOnKillCascade(false)
                .state(ExecutionKilled.State.EXECUTED)
                .tenantId(message.getTenantId())
                .build()
            );
        } catch (QueueException e) {
            log.error("Unable to kill the execution {}", message.getExecutionId(), e);
        }

        Optional<ExecutorContext> maybeExecutor = killingOrAfterKillState(message.getExecutionId(), Optional.ofNullable(message.getExecutionState()));

        // Check whether kill event should be propagated to downstream executions.
        // By default, always propagate the ExecutionKill to sub-flows (for backward compatibility).
        Boolean isOnKillCascade = Optional.ofNullable(message.getIsOnKillCascade()).orElse(true);
        if (isOnKillCascade) {
            executionService
                .killSubflowExecutions(message.getTenantId(), message.getExecutionId())
                .doOnNext(executionKilled -> {
                    try {
                        killQueue.emit(executionKilled);
                    } catch (QueueException e) {
                        log.error("Unable to kill the execution {}", executionKilled.getExecutionId(), e);
                    }
                })
                .blockLast();
        }

        return maybeExecutor;
    }

    private Optional<ExecutorContext> killingOrAfterKillState(final String executionId, Optional<State.Type> afterKillState) {
        return executionStateStore.lock(executionId, execution -> {
            FlowInterface flow = flowMetaStore.findByExecution(execution).orElseThrow();

            // remove it from the queued store if it was queued so it would not be restarted
            if (execution.getState().isQueued()) {
                executionQueuedStateStore.remove(execution);
            }

            Execution killing = executionService.kill(execution, flow, afterKillState);
            return new ExecutorContext(execution)
                .withExecution(killing, "joinKillingExecution");
        });
    }
}
