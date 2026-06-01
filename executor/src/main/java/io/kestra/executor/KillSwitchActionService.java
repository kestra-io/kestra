package io.kestra.executor;

import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Performs kill-switch actions (KILL, CANCEL, IGNORE) on existing executions.
 *
 * <p>Complements {@link io.kestra.core.killswitch.KillSwitchService}, which evaluates
 * <em>whether</em> a kill switch applies. This service performs the resulting <em>action</em>.</p>
 */
@Singleton
@Slf4j
public class KillSwitchActionService {
    private static final String IGNORING_EXECUTION_MSG = "Ignoring execution {} because there is a kill switch on it";
    private static final String CANCELLING_EXECUTION_MSG = "Cancelling execution {} because there is a kill switch on it";
    private static final String KILLING_EXECUTION_MSG = "Killing execution {} because there is a kill switch on it";

    private final ExecutionStateStore executionStateStore;
    private final BroadcastQueueInterface<ExecutionKilled> killQueue;

    @Inject
    public KillSwitchActionService(
        ExecutionStateStore executionStateStore,
        BroadcastQueueInterface<ExecutionKilled> killQueue) {
        this.executionStateStore = executionStateStore;
        this.killQueue = killQueue;
    }

    /**
     * Dispatches the appropriate kill-switch action for an existing execution.
     *
     * @param evaluationType the kill-switch verdict — must not be {@link EvaluationType#PASS}
     * @param tenantId       tenant owning the execution
     * @param executionId    execution to act on
     */
    public void handle(EvaluationType evaluationType, String tenantId, String executionId) {
        switch (evaluationType) {
            case IGNORE -> log.warn(IGNORING_EXECUTION_MSG, executionId);
            case KILL -> {
                log.warn(KILLING_EXECUTION_MSG, executionId);
                killExecution(tenantId, executionId);
            }
            case CANCEL -> {
                log.warn(CANCELLING_EXECUTION_MSG, executionId);
                cancelExecution(executionId);
            }
        }
    }

    private void killExecution(String tenantId, String executionId) {
        executionStateStore.lock(executionId, execution -> {
            if (!execution.getState().isTerminated()) {
                var newExecution = execution.withState(State.Type.KILLING).addLabel(new Label(Label.KILL_SWITCH, "killed"));
                return new ExecutorContext(newExecution);
            }
            return null;
        });
        try {
            killQueue.emit(
                ExecutionKilledExecution.builder()
                    .tenantId(tenantId)
                    .executionId(executionId)
                    .isOnKillCascade(true)
                    .state(ExecutionKilled.State.REQUESTED)
                    .build()
            );
        } catch (QueueException e) {
            log.error("Unable to kill the execution {}", executionId, e);
        }
    }

    private void cancelExecution(String executionId) {
        executionStateStore.lock(executionId, execution -> {
            if (!execution.getState().isTerminated()) {
                var newExecution = execution.withState(State.Type.CANCELLED).addLabel(new Label(Label.KILL_SWITCH, "cancelled"));
                return new ExecutorContext(newExecution);
            }
            return null;
        });
    }
}
