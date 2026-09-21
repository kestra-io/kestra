package io.kestra.core.services;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Pair;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.FollowExecutionEvent;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;

import io.micronaut.http.sse.Event;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.FluxSink;

/**
 * This service offers a fanout mechanism so a single consumer of the execution queue can dispatch execution
 * messages to multiple consumers.
 * It is designed to be used for 'follow' endpoints that use SSE to follow a flow execution.
 * <p>
 * Consumers need first to register themselves via {@link #registerSubscriber(String, String, FluxSink, Flow)},
 * then unregister (ideally in a finally block to avoid any memory leak) via {@link #unregisterSubscriber(String, String)}.
 */
@Slf4j
@Singleton
public class ExecutionStreamingService {
    private final Map<String, Map<String, Pair<FluxSink<Event<Execution>>, Flow>>> subscribers = new ConcurrentHashMap<>();
    private final Object subscriberLock = new Object();

    private final BroadcastQueueInterface<FollowExecutionEvent> executionQueue;
    private final ExecutionService executionService;
    private final ExecutionRepositoryInterface executionRepository;

    private QueueSubscriber<FollowExecutionEvent> queueSubscriber;

    @Inject
    public ExecutionStreamingService(
        BroadcastQueueInterface<FollowExecutionEvent> executionQueue,
        ExecutionService executionService,
        ExecutionRepositoryInterface executionRepository) {
        this.executionQueue = executionQueue;
        this.executionService = executionService;
        this.executionRepository = executionRepository;
    }

    @PostConstruct
    void startQueueConsumer() {
        // Single queue consumer
        this.queueSubscriber = executionQueue.subscriber();
        this.queueSubscriber.pause();
        this.queueSubscriber.subscribe(this::dispatch);
    }

    /**
     * Dispatch an event to all the subscribers of its execution.
     * This method never throws: the queue subscriber is shared by all subscribers and treats any escaping
     * exception as fatal, so a delivery failure to a single SSE stream would shut down the whole server.
     */
    private void dispatch(Either<FollowExecutionEvent, DeserializationException> either) {
        try {
            if (either.isRight()) {
                log.error("Unable to deserialize execution: {}", either.getRight().getMessage());
                return;
            }

            if (subscribers.isEmpty()) {
                return;
            }

            FollowExecutionEvent event = either.getLeft();

            // Get all subscribers for this execution
            Map<String, Pair<FluxSink<Event<Execution>>, Flow>> executionSubscribers = subscribers.get(event.executionId());

            if (!MapUtils.isEmpty(executionSubscribers)) {
                // This fan-out runs on the queue-polling thread, which has no authenticated principal,
                // so the ACL-enforcing findById would always deny authorization on EE.
                Optional<Execution> execution = executionRepository.findByIdWithoutAcl(event.tenantId(), event.executionId());
                if (execution.isEmpty()) {
                    log.error("Unable to find the execution id {}", event.executionId());
                    return;
                }

                executionSubscribers.forEach(
                    (subscriberId, pair) -> deliver(event.executionId(), subscriberId, pair.getLeft(), pair.getRight(), execution.get())
                );
            }
        } catch (Exception e) {
            log.error("Unable to dispatch the execution event to its subscribers", e);
        }
    }

    /**
     * Deliver an execution update to a single subscriber.
     * This method never throws so a stale or broken SSE stream cannot prevent delivery to the other subscribers.
     */
    private void deliver(String executionId, String subscriberId, FluxSink<Event<Execution>> sink, Flow flow, Execution execution) {
        if (sink.isCancelled()) {
            // the SSE stream is already closed: drop the stale subscriber instead of writing to it
            unregisterSubscriber(executionId, subscriberId);
            return;
        }

        try {
            if (isStopFollow(flow, execution)) {
                sink.next(Event.of(execution).id("end"));
                sink.complete();
            } else {
                sink.next(Event.of(execution).id("progress"));
            }
        } catch (Exception e) {
            log.error("Error sending execution update to the subscriber '{}'", subscriberId, e);
            failSilently(sink, e);
            unregisterSubscriber(executionId, subscriberId);
        }
    }

    /**
     * Fail the sink, ignoring any error raised by an already terminated one.
     */
    private void failSilently(FluxSink<Event<Execution>> sink, Exception cause) {
        try {
            sink.error(cause);
        } catch (Exception e) {
            log.debug("Unable to fail an already terminated sink", e);
        }
    }

    /**
     * Register a subscriber to an execution.
     * All subscribers must ensure to call {@link #unregisterSubscriber(String, String)} to avoid any memory leak.
     */
    public void registerSubscriber(String executionId, String subscriberId, FluxSink<Event<Execution>> sink, Flow flow) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            // Register the subscriber BEFORE resuming the queue. If the queue is resumed first,
            // the polling thread can deliver a terminal FollowExecutionEvent between resume() and put(),
            // missing the event and leaving Flux.last() hanging forever.
            subscribers.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>())
                .put(subscriberId, Pair.of(sink, flow));

            if (this.queueSubscriber.isPaused()) {
                this.queueSubscriber.resume();
            }
        }

        // Guard against the race where the queue was already running (other subscribers
        // exist) and the terminal FollowExecutionEvent arrived and was consumed before
        // this subscriber was registered. In that case the event is lost and Flux.last()
        // would hang forever. Recover by checking the current execution state: if it is
        // already in a terminal state, deliver the "end" event directly.
        // FluxSink is thread-safe: duplicate complete() calls are no-ops, and next()
        // after complete() is silently dropped, so double-delivery is harmless.
        // Uses the no-ACL lookup: registerSubscriber's caller thread varies, so the ACL-enforcing findById would always
        // deny authorization in EE
        executionRepository.findByIdWithoutAcl(flow.getTenantId(), executionId).ifPresent(execution ->
        {
            if (isStopFollow(flow, execution)) {
                sink.next(Event.of(execution).id("end"));
                sink.complete();
            }
        });
    }

    /**
     * Unregister a subscribers.
     * This is advised to do it in a finally block to be sure to free resources.
     */
    public void unregisterSubscriber(String executionId, String subscriberId) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            Map<String, Pair<FluxSink<Event<Execution>>, Flow>> executionSubscribers = subscribers.get(executionId);
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

    /**
     * Utility method to know if following an execution can be stopped.
     */
    public boolean isStopFollow(Flow flow, Execution execution) {
        return executionService.isTerminated(flow, execution) &&
            ListUtils.emptyOnNull(execution.getTaskRunList()).stream().allMatch(taskRun -> taskRun.getState().isTerminated());
    }

    @PreDestroy
    void shutdown() {
        if (queueSubscriber != null) {
            queueSubscriber.close();
        }
    }
}