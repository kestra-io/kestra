package io.kestra.core.services;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Pair;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.runners.FollowLogEvent;
import io.kestra.core.utils.Either;
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
 * <p>
 * Each subscriber is registered with a list of {@link QueryFilter}s. Incoming events are matched
 * against those filters via the injected {@link Searchable}; only events that match every filter
 * are forwarded to that subscriber.
 */
@Slf4j
@Singleton
public class LogStreamingService {
    private final Map<String, Map<String, Pair<FluxSink<Event<FollowLogEvent>>, List<QueryFilter>>>> subscribers = new ConcurrentHashMap<>();
    private final Object subscriberLock = new Object();

    @Inject
    protected BroadcastQueueInterface<FollowLogEvent> logQueue;

    @Inject
    protected FollowLogEventMatcher followLogEventMatcher;

    private QueueSubscriber<FollowLogEvent> queueSubscriber;

    @PostConstruct
    void startQueueConsumer() {
        this.queueSubscriber = logQueue.subscriber();
        this.queueSubscriber.pause();
        this.queueSubscriber.subscribe(this::dispatch);
    }

    /**
     * Dispatch a log event to all the subscribers of its execution.
     * This method never throws: the queue subscriber is shared by all subscribers and treats any escaping
     * exception as fatal, so a delivery failure to a single SSE stream would shut down the whole server.
     */
    private void dispatch(Either<FollowLogEvent, DeserializationException> either) {
        try {
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
            Map<String, Pair<FluxSink<Event<FollowLogEvent>>, List<QueryFilter>>> executionSubscribers = subscribers.get(current.executionId());

            if (executionSubscribers != null && !executionSubscribers.isEmpty()) {
                executionSubscribers.forEach((subscriberId, pair) -> deliver(current, subscriberId, pair.getLeft(), pair.getRight()));
            }
        } catch (Exception e) {
            log.error("Unable to dispatch the log event to its subscribers", e);
        }
    }

    /**
     * Deliver a log event to a single subscriber.
     * This method never throws so a stale or broken SSE stream cannot prevent delivery to the other subscribers.
     */
    private void deliver(FollowLogEvent current, String subscriberId, FluxSink<Event<FollowLogEvent>> sink, List<QueryFilter> filters) {
        if (sink.isCancelled()) {
            // the SSE stream is already closed: drop the stale subscriber instead of writing to it
            unregisterSubscriber(current.executionId(), subscriberId);
            return;
        }

        try {
            if (followLogEventMatcher.matches(current, filters)) {
                sink.next(Event.of(current).id("progress"));
            }
        } catch (Exception e) {
            log.error("Error sending log update to the subscriber '{}'", subscriberId, e);
            failSilently(sink, e);
            unregisterSubscriber(current.executionId(), subscriberId);
        }
    }

    /**
     * Fail the sink, ignoring any error raised by an already terminated one.
     */
    private void failSilently(FluxSink<Event<FollowLogEvent>> sink, Exception cause) {
        try {
            sink.error(cause);
        } catch (Exception e) {
            log.debug("Unable to fail an already terminated sink", e);
        }
    }

    /**
     * Register a subscriber to an execution logs. The provided {@code filters} are applied to
     * every event before it is forwarded; an empty or {@code null} list forwards everything.
     * All subscribers must ensure to call {@link #unregisterSubscriber(String, String)} to avoid any memory leak.
     */
    public void registerSubscriber(String executionId, String subscriberId, FluxSink<Event<FollowLogEvent>> sink, List<QueryFilter> filters) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            // Register the subscriber BEFORE resuming the queue to avoid a race where the polling
            // thread delivers an event between resume() and put(), causing events to be dropped.
            subscribers.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>())
                .put(subscriberId, Pair.of(sink, filters));

            if (this.queueSubscriber.isPaused()) {
                this.queueSubscriber.resume();
            }
        }
    }

    /**
     * Unregister a subscribers.
     * This is advised to do it in a finally block to be sure to free resources.
     */
    public void unregisterSubscriber(String executionId, String subscriberId) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            Map<String, Pair<FluxSink<Event<FollowLogEvent>>, List<QueryFilter>>> executionSubscribers = subscribers.get(executionId);
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
