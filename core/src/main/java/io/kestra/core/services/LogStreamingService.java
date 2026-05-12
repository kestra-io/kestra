package io.kestra.core.services;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Pair;

import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.runners.FollowLogEvent;
import io.kestra.core.utils.MapUtils;
import io.micronaut.http.sse.Event;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.FluxSink;

/**
 * This service offers a fanout mechanism so a single consumer of the log queue can dispatch log messages to multiple consumers.
 * It is designed to be used for 'follow' endpoints that using SSE to follow a flow logs.
 * <p>
 * Consumers need first to register themselves via {@link #registerSubscriber(String, String, FluxSink, List)},
 * then unregister (ideally in a finally block to avoid any memory leak) via {@link #unregisterSubscriber(String, String)}.
 */
@Slf4j
@Singleton
public class LogStreamingService {
    private final Map<String, Map<String, Pair<FluxSink<Event<FollowLogEvent>>, List<String>>>> subscribers = new ConcurrentHashMap<>();
    private final Object subscriberLock = new Object();

    @Inject
    protected BroadcastQueueInterface<FollowLogEvent> logQueue;

    private QueueSubscriber<FollowLogEvent> queueSubscriber;

    @PostConstruct
    void startQueueConsumer() {
        this.queueSubscriber = logQueue.subscriber();
        this.queueSubscriber.pause();
        this.queueSubscriber.subscribe(either ->
        {
            if (either.isRight()) {
                log.error("Unable to deserialize log: {}", either.getRight().getMessage());
                return;
            }

            if (subscribers.isEmpty()) {
                return;
            }

            FollowLogEvent current = either.getLeft();
            if (current.executionId() == null) {
                // some logs are not about any execution, we skip them
                return;
            }

            // Get all subscribers for this execution
            Map<String, Pair<FluxSink<Event<FollowLogEvent>>, List<String>>> executionSubscribers = subscribers.get(current.executionId());

            if (executionSubscribers != null && !executionSubscribers.isEmpty()) {
                executionSubscribers.values().forEach(pair ->
                {
                    var sink = pair.getLeft();
                    var levels = pair.getRight();

                    if (levels.contains(current.level().name())) {
                        sink.next(Event.of(current).id("progress"));
                    }
                });
            }
        });
    }

    /**
     * Register a subscriber to an execution logs.
     * All subscribers must ensure to call {@link #unregisterSubscriber(String, String)} to avoid any memory leak.
     */
    public void registerSubscriber(String executionId, String subscriberId, FluxSink<Event<FollowLogEvent>> sink, List<String> levels) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            // resume the subscription if paused
            if (MapUtils.isEmpty(subscribers) && this.queueSubscriber.isPaused()) {
                this.queueSubscriber.resume();
            }

            subscribers.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>())
                .put(subscriberId, Pair.of(sink, levels));
        }
    }

    /**
     * Unregister a subscribers.
     * This is advised to do it in a finally block to be sure to free resources.
     */
    public void unregisterSubscriber(String executionId, String subscriberId) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            Map<String, Pair<FluxSink<Event<FollowLogEvent>>, List<String>>> executionSubscribers = subscribers.get(executionId);
            if (executionSubscribers != null) {
                executionSubscribers.remove(subscriberId);
                if (executionSubscribers.isEmpty()) {
                    subscribers.remove(executionId);
                }
            }

            // pause the subscription if no one is listening anymore
            if (MapUtils.isEmpty(subscribers) && !this.queueSubscriber.isPaused()) {
                this.queueSubscriber.pause();
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
