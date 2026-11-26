package io.kestra.scheduler.pubsub;

import org.slf4j.event.Level;

import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.utils.Logs;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Kills the instances of triggers running on a worker.
 */
@Singleton
public class TriggerKillPublisher {

    private final BroadcastQueueInterface<ExecutionKilled> executionKilledQueue;

    @Inject
    public TriggerKillPublisher(final BroadcastQueueInterface<ExecutionKilled> executionKilledQueue) {
        this.executionKilledQueue = executionKilledQueue;
    }

    /**
     * Kills the running instance of a realtime trigger, if any. A realtime trigger runs for its whole
     * lifetime on a worker and is not affected by state changes (update, disable, delete) until killed.
     */
    public void maySendExecutionKilled(final TriggerState state) {
        if (TriggerType.REALTIME.equals(state.getType())) {
            sendExecutionKilled(state, state.getDispatchEpoch());
        }
    }

    public void sendExecutionKilled(final TriggerId id) {
        sendExecutionKilled(id, null);
    }

    private void sendExecutionKilled(final TriggerId id, final Long dispatchEpoch) {
        try {
            this.executionKilledQueue.emit(
                ExecutionKilledTrigger
                    .builder()
                    // Trigger kills are not processed by the Executor: emit them directly in the
                    // EXECUTED state, the only state forwarded to the workers.
                    .state(ExecutionKilled.State.EXECUTED)
                    .tenantId(id.getTenantId())
                    .namespace(id.getNamespace())
                    .flowId(id.getFlowId())
                    .triggerId(id.getTriggerId())
                    .dispatchEpoch(dispatchEpoch)
                    .build()
            );
        } catch (QueueException e) {
            Logs.logTrigger(id, Level.WARN, "Cannot kill a real-time trigger, it will continue processing until Kestra is restarted. Cause: {}", e.getMessage(), e);
        }
    }
}
