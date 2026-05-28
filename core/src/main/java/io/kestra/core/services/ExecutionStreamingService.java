package io.kestra.core.services;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Pair;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.FollowExecutionEvent;
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
        this.queueSubscriber.subscribe(either ->
        {
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
                Optional<Execution> execution = executionRepository.findById(event.tenantId(), event.executionId());
                if (execution.isEmpty()) {
                    log.error("Unable to find the execution id {}", event.executionId());
                    return;
                }

                executionSubscribers.values().forEach(pair ->
                {
                    var sink = pair.getLeft();
                    var flow = pair.getRight();
                    try {
                        if (isStopFollow(flow, execution.get())) {
                            sink.next(Event.of(execution.get()).id("end"));
                            sink.complete();
                        } else {
                            sink.next(Event.of(execution.get()).id("progress"));
                        }
                    } catch (Exception e) {
                        log.error("Error sending execution update", e);
                        sink.error(e);
                    }
                });
            }
        });
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