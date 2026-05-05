package io.kestra.core.async;

import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.utils.MapUtils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.FluxSink;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fan-out service for {@link AsyncOperationProcessedEvent}.
 * <p>
 * Controllers performing blocking single-action APIs register a subscriber keyed by
 * {@code (operationId, itemId)} before emitting the domain event; the service dispatches
 * the incoming processed event to the matching subscriber and completes the sink.
 * <p>
 * Mirrors {@link io.kestra.core.services.ExecutionStreamingService}: subscribers are held in a nested
 * {@link ConcurrentHashMap} guarded by a single lock for add/remove atomicity.
 */
@Slf4j
@Singleton
public class AsyncEventStreamingService {
    private final Map<String, Map<String, FluxSink<AsyncOperationProcessedEvent>>> subscribers = new ConcurrentHashMap<>();
    private final Object subscriberLock = new Object();

    private final BroadcastQueueInterface<AsyncOperationProcessedEvent> asyncOperationQueue;

    private QueueSubscriber<AsyncOperationProcessedEvent> queueSubscriber;

    @Inject
    public AsyncEventStreamingService(BroadcastQueueInterface<AsyncOperationProcessedEvent> asyncOperationQueue) {
        this.asyncOperationQueue = asyncOperationQueue;
    }

    @PostConstruct
    void startQueueConsumer() {
        // Single queue consumer
        this.queueSubscriber = asyncOperationQueue.subscriber().subscribe(either ->
        {
            if (either.isRight()) {
                log.error("Unable to deserialize AsyncOperationProcessedEvent: {}", either.getRight().getMessage());
                return;
            }

            if (subscribers.isEmpty()) {
                return;
            }

            AsyncOperationProcessedEvent event = either.getLeft();

            // Get all subscribers for this operation
            Map<String, FluxSink<AsyncOperationProcessedEvent>> operationSubscribers = subscribers.get(event.operationId());

            if (MapUtils.isEmpty(operationSubscribers)) {
                return;
            }

            FluxSink<AsyncOperationProcessedEvent> sink = operationSubscribers.get(event.itemId());
            if (sink == null) {
                return;
            }

            try {
                sink.next(event);
                sink.complete();
            } catch (Exception e) {
                log.error("Error dispatching AsyncOperationProcessedEvent for op {} item {}",
                    event.operationId(), event.itemId(), e);
                sink.error(e);
            }
        });
    }

    /**
     * Register a subscriber for a specific {@code (operationId, itemId)} pair.
     * The sink will receive exactly one event and then complete.
     * All subscribers must ensure to call {@link #unregisterSubscriber(String, String)} to avoid any memory leak.
     */
    public void registerSubscriber(String operationId, String itemId, FluxSink<AsyncOperationProcessedEvent> sink) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            subscribers.computeIfAbsent(operationId, k -> new ConcurrentHashMap<>())
                .put(itemId, sink);
        }
    }

    /**
     * Unregister a subscriber.
     * This is advised to do it in a finally block to be sure to free resources.
     */
    public void unregisterSubscriber(String operationId, String itemId) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            Map<String, FluxSink<AsyncOperationProcessedEvent>> operationSubscribers = subscribers.get(operationId);
            if (operationSubscribers != null) {
                operationSubscribers.remove(itemId);
                if (operationSubscribers.isEmpty()) {
                    subscribers.remove(operationId);
                }
            }
        }
    }

    @PreDestroy
    void shutdown() {
        if (queueSubscriber != null) {
            queueSubscriber.close();
        }
    }
}
