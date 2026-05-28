package io.kestra.core.async;

import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Objects;

/**
 * Emits {@link AsyncOperationProcessedEvent} on behalf of domain consumers that
 * handle events implementing {@link AsyncOperation}.
 * <p>
 * Consumers call {@link #emitProcessedIfAsync} in a {@code finally} block after
 * processing the domain event. If the message does not belong to an async operation
 * (i.e. {@code operationId} is {@code null}), the call is a no-op.
 */
@Singleton
@Slf4j
public class AsyncOperationService {

    private final BroadcastQueueInterface<AsyncOperationProcessedEvent> asyncOperationProcessedEventQueue;

    @Inject
    public AsyncOperationService(BroadcastQueueInterface<AsyncOperationProcessedEvent> asyncOperationProcessedEventQueue) {
        this.asyncOperationProcessedEventQueue = Objects.requireNonNull(asyncOperationProcessedEventQueue);
    }

    /**
     * Emits an {@link AsyncOperationProcessedEvent} when {@code message} carries a non-null
     * {@link AsyncOperation#operationId() operation id}. Otherwise, no-op.
     *
     * @param message  the processed domain message.
     * @param tenantId the tenant of the affected resource.
     * @param itemId   the uid of the affected resource (e.g. execution id, trigger uid).
     * @param outcome  the processing outcome.
     * @param error    the error message when {@code outcome} is {@code FAILED}, otherwise {@code null}.
     */
    public void emitProcessedIfAsync(AsyncOperation message,
                                     String tenantId,
                                     String itemId,
                                     AsyncOperationProcessedEvent.Outcome outcome,
                                     @Nullable String error) {
        String operationId = message.operationId();
        if (operationId == null) {
            return;
        }
        try {
            asyncOperationProcessedEventQueue.emit(new AsyncOperationProcessedEvent(
                operationId, tenantId, itemId, outcome, error, Instant.now()
            ));
        } catch (QueueException e) {
            log.error("Failed to emit AsyncOperationProcessedEvent for op {} item {}. Ignored", operationId, itemId, e);
        }
    }
}
