package io.kestra.webserver.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;
import io.kestra.webserver.controllers.api.ExecutionStatusEvent;
import io.micronaut.http.sse.Event;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.FluxSink;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static io.kestra.core.models.Label.CORRELATION_ID;

/**
 * This service offers a fanout mechanism so a single consumer of the execution queue can dispatch execution
 * messages to multiple consumers.
 * It is designed to be used for 'follow-dependencies' endpoints that use SSE to follow a set of flow dependency executions.
 * <p>
 * Consumers need first to register themselves via {@link #registerSubscriber(String, String, Subscriber)},
 * then unregister (ideally in a finally block to avoid any memory leak) via {@link #unregisterSubscriber(String, String)}.
 */
@Slf4j
@Singleton
public class ExecutionDependenciesStreamingService {
    private final Map<String, Map<String, Subscriber>> subscribers = new ConcurrentHashMap<>();
    private final Object subscriberLock = new Object();

    private final QueueInterface<Execution> executionQueue;
    private final ExecutionService executionService;

    private Runnable queueConsumer;

    public record Subscriber(String correlationId, List<FlowNode> dependencies, Map<String, Flow> flows, FluxSink<Event<ExecutionStatusEvent>> sink) {}

    @Inject
    public ExecutionDependenciesStreamingService(
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        ExecutionService executionService
    ) {
        this.executionQueue = executionQueue;
        this.executionService = executionService;
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
            Optional<String> correlationId = execution.getLabels().stream().filter(label -> label.key().equals(CORRELATION_ID)).findAny().map(label -> label.value());

            // Get all subscribers for this correlationId
            if (correlationId.isPresent()) {
                Map<String, Subscriber> executionSubscribers = subscribers.get(correlationId.get());

                if (!MapUtils.isEmpty(executionSubscribers)) {
                    executionSubscribers.values().forEach(consumer -> {
                        var sink = consumer.sink();
                        if (isADependency(execution, consumer.dependencies(), correlationId.get())) {
                            var flow = consumer.flows.get(executionId);
                            try {
                                if (isStopFollow(flow, execution)) {
                                    sink.next(Event.of(ExecutionStatusEvent.of(execution)).id("end"));
                                    // remove it from dependencies so we know when all dependencies are terminated
                                    consumer.dependencies().removeIf(node -> node.getTenantId().equals(execution.getTenantId()) && node.getNamespace().equals(execution.getNamespace()) && node.getId().equals(execution.getFlowId()));
                                } else {
                                    sink.next(Event.of(ExecutionStatusEvent.of(execution)).id("progress"));
                                }

                                // end the flux if there are no more dependencies to follow
                                if (consumer.dependencies().isEmpty()) {
                                    sink.next(Event.of(ExecutionStatusEvent.of(Execution.builder().id(executionId).build())).id("end-all"));
                                    sink.complete();
                                }
                            } catch (Exception e) {
                                log.error("Error sending execution update", e);
                                sink.error(e);
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * Register a subscriber to an execution.
     * All subscribers must ensure to call {@link #unregisterSubscriber(String, String)} to avoid any memory leak.
     */
    public void registerSubscriber(String correlationId, String subscriberId, Subscriber consumer) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            subscribers.computeIfAbsent(correlationId, k -> new ConcurrentHashMap<>())
                .put(subscriberId, consumer);
        }
    }

    /**
     * Unregister a subscribers.
     * This is advised to do it in a finally block to be sure to free resources.
     */
    public void unregisterSubscriber(String correlationId, String subscriberId) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            Map<String, Subscriber> executionSubscribers = subscribers.get(correlationId);
            if (executionSubscribers != null) {
                executionSubscribers.remove(subscriberId);
                if (executionSubscribers.isEmpty()) {
                    subscribers.remove(correlationId);
                }
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
        if (queueConsumer != null) {
            queueConsumer.run();
        }
    }

    private boolean isADependency(Execution execution, List<FlowNode> nodes, String correlationId) {
        return execution.getLabels().stream().anyMatch(label -> label.key().equals(CORRELATION_ID) && label.value().equals(correlationId)) &&
            nodes.stream().anyMatch(node -> node.getTenantId().equals(execution.getTenantId()) && node.getNamespace().equals(execution.getNamespace()) && node.getId().equals(execution.getFlowId()));
    }
}
