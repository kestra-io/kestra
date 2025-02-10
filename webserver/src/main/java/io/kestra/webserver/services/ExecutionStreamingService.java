package io.kestra.webserver.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.services.ConditionService;
import io.micronaut.http.sse.Event;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import reactor.core.publisher.FluxSink;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This service offers a fanout mechanism so a single consumer of the execution queue can dispatch execution
 * messages to multiple consumers.
 * It is designed to be used for 'follow' endpoints that using SSE to follow a flow execution.
 * <p>
 * Consumers need first to register themselves via {@link #registerSubscriber(String, String, FluxSink, Flow)},
 * then unregister (ideally in a finally block to avoid any memory leak) via {@link #unregisterSubscriber(String, String)}.
 */
@Slf4j
@Singleton
public class ExecutionStreamingService {
    private final Map<String, Map<String, Pair<FluxSink<Event<Execution>>, Flow>>> subscribers = new ConcurrentHashMap<>();
    private final Object subscriberLock = new Object();

    private final QueueInterface<Execution> executionQueue;
    private final ConditionService conditionService;

    private Runnable queueConsumer;

    @Inject
    public ExecutionStreamingService(
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        ConditionService conditionService
    ) {
        this.executionQueue = executionQueue;
        this.conditionService = conditionService;
    }

    @PostConstruct
    void startQueueConsumer() {
        // Single queue consumer
        this.queueConsumer = executionQueue.receive(either -> {
            if (either.isRight()) {
                log.error("Unable to deserialize execution: {}", either.getRight().getMessage());
                return;
            }

            Execution execution = either.getLeft();
            String executionId = execution.getId();

            // Get all subscribers for this execution
            Map<String, Pair<FluxSink<Event<Execution>>, Flow>> executionSubscribers = subscribers.get(executionId);

            if (executionSubscribers != null && !executionSubscribers.isEmpty()) {
                executionSubscribers.values().forEach(pair -> {
                    var sink = pair.getLeft();
                    var flow = pair.getRight();
                    try {
                        if (isStopFollow(flow, execution)) {
                            sink.next(Event.of(execution).id("end"));
                            sink.complete();
                        } else {
                            sink.next(Event.of(execution).id("progress"));
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
            subscribers.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>())
                .put(subscriberId, Pair.of(sink, flow));
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
        }
    }

    /**
     * Utility method to know if following an execution can be stopped.
     */
    public boolean isStopFollow(Flow flow, Execution execution) {
        return conditionService.isTerminatedWithListeners(flow, execution) &&
            execution.getState().getCurrent() != State.Type.PAUSED;
    }

    @PreDestroy
    void shutdown() {
        if (queueConsumer != null) {
            queueConsumer.run();
        }
    }
}