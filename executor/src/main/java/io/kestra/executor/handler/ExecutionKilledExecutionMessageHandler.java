package io.kestra.executor.handler;

import java.util.Optional;

import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.async.AsyncOperationService;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.ExecutorMessageHandler;
import io.kestra.executor.ExecutorService;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

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
    private BroadcastQueueInterface<ExecutionKilled> killQueue;

    @Inject
    private AsyncOperationService asyncOperationService;

    @Inject
    private KillSwitchService killSwitchService;

    @Override
    public Optional<ExecutorContext> handle(ExecutionKilledExecution message) {
        // Only IGNORE is filtered here — KILL and CANCEL still need to process the kill event.
        if (killSwitchService.evaluate(message.getExecutionId()) == EvaluationType.IGNORE) {
            log.warn("Ignoring execution {} because there is a kill switch on it", message.getExecutionId());
            return Optional.empty();
        }
        AsyncOperationProcessedEvent.Outcome outcome = AsyncOperationProcessedEvent.Outcome.SUCCEEDED;
        String error = null;
        try {
            return doHandle(message);
        } catch (RuntimeException e) {
            outcome = AsyncOperationProcessedEvent.Outcome.FAILED;
            error = e.getMessage();
            throw e;
        } finally {
            asyncOperationService.emitProcessedIfAsync(message, message.getTenantId(), message.getExecutionId(), outcome, error);
        }
    }

    private Optional<ExecutorContext> doHandle(ExecutionKilledExecution message) {
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
            killQueue.emit(
                ExecutionKilledExecution
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
                .doOnNext(executionKilled ->
                {
                    try {
                        killQueue.emit(executionKilled);
                    } catch (QueueException e) {
                        log.error("Unable to kill the execution {}", executionKilled.getExecutionId(), e);
                    }
                })
                .blockLast();

            // Also kill loop sub-executions created by a Loop task within this execution.
            for (ExecutionKilledExecution executionKilled : executionService.killLoopSubExecutions(message.getTenantId(), message.getExecutionId())) {
                try {
                    killQueue.emit(executionKilled);
                } catch (QueueException e) {
                    log.error("Unable to kill the loop sub-execution {}", executionKilled.getExecutionId(), e);
                }
            }
        }

        return maybeExecutor;
    }

    private Optional<ExecutorContext> killingOrAfterKillState(final String executionId, Optional<State.Type> afterKillState) {
        return executionStateStore.lock(executionId, execution ->
        {
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
