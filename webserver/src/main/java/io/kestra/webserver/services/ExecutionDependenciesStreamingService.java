package io.kestra.webserver.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.FollowExecutionEvent;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;
import io.kestra.webserver.controllers.api.ExecutionStatusEvent;

import io.micronaut.http.sse.Event;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.FluxSink;

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

    private final BroadcastQueueInterface<FollowExecutionEvent> executionQueue;
    private final ExecutionService executionService;
    private final ExecutionRepositoryInterface executionRepositoryInterface;

    private QueueSubscriber<FollowExecutionEvent> queueSubscriber;

    public record Subscriber(String correlationId, List<FlowNode> dependencies, Map<String, Flow> flows, FluxSink<Event<ExecutionStatusEvent>> sink) {
    }

    @Inject
    public ExecutionDependenciesStreamingService(
        BroadcastQueueInterface<FollowExecutionEvent> executionQueue,
        ExecutionService executionService,
        ExecutionRepositoryInterface executionRepositoryInterface) {
        this.executionQueue = executionQueue;
        this.executionService = executionService;
        this.executionRepositoryInterface = executionRepositoryInterface;
    }

    @PostConstruct
    void startQueueConsumer() {
        // Single queue consumer
        this.queueSubscriber = executionQueue.subscriber();
        this.queueSubscriber.pause();
        this.queueSubscriber.subscribe(this::dispatch);
    }

    /**
     * Dispatch an event to all the subscribers following its correlation id.
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

            String executionId = either.getLeft().executionId();
            // This fan-out runs on the queue-polling thread, which has no authenticated principal,
            // so the ACL-enforcing findById would always deny authorization on EE.
            Optional<Execution> maybeExecution = executionRepositoryInterface.findByIdWithoutAcl(either.getLeft().tenantId(), executionId);
            if (maybeExecution.isEmpty()) {
                log.error("Unable to find the execution id {}", executionId);
                return;
            }
            Execution execution = maybeExecution.get();
            Optional<String> correlationId = execution.getLabels().stream().filter(label -> label.key().equals(CORRELATION_ID)).findAny().map(label -> label.value());

            // Get all subscribers for this correlationId
            if (correlationId.isPresent()) {
                Map<String, Subscriber> executionSubscribers = subscribers.get(correlationId.get());

                if (!MapUtils.isEmpty(executionSubscribers)) {
                    executionSubscribers.forEach((subscriberId, consumer) -> deliver(correlationId.get(), subscriberId, consumer, execution));
                }
            }
        } catch (Exception e) {
            log.error("Unable to dispatch the execution event to its subscribers", e);
        }
    }

    /**
     * Deliver an execution update to a single subscriber.
     * This method never throws so a stale or broken SSE stream cannot prevent delivery to the other subscribers.
     */
    private void deliver(String correlationId, String subscriberId, Subscriber consumer, Execution execution) {
        if (!isADependency(execution, consumer.dependencies(), correlationId)) {
            return;
        }

        var sink = consumer.sink();
        if (sink.isCancelled()) {
            // the SSE stream is already closed: drop the stale subscriber instead of writing to it
            unregisterSubscriber(correlationId, subscriberId);
            return;
        }

        var flow = consumer.flows().get(FlowId.uidWithoutRevision(execution));
        try {
            if (isStopFollow(flow, execution)) {
                sink.next(Event.of(ExecutionStatusEvent.of(execution)).id("end"));
                // remove it from dependencies so we know when all dependencies are terminated
                consumer.dependencies().removeIf(
                    node -> node.getTenantId().equals(execution.getTenantId()) && node.getNamespace().equals(execution.getNamespace())
                        && node.getId().equals(execution.getFlowId())
                );
            } else {
                sink.next(Event.of(ExecutionStatusEvent.of(execution)).id("progress"));
            }

            // end the flux if there are no more dependencies to follow
            if (consumer.dependencies().isEmpty()) {
                sink.next(Event.of(ExecutionStatusEvent.of(Execution.builder().id(execution.getId()).build())).id("end-all"));
                sink.complete();
            }
        } catch (Exception e) {
            log.error("Error sending execution update to the subscriber '{}'", subscriberId, e);
            failSilently(sink, e);
            unregisterSubscriber(correlationId, subscriberId);
        }
    }

    /**
     * Fail the sink, ignoring any error raised by an already terminated one.
     */
    private void failSilently(FluxSink<Event<ExecutionStatusEvent>> sink, Exception cause) {
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
    public void registerSubscriber(String correlationId, String subscriberId, Subscriber consumer) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            // Register the subscriber BEFORE resuming the queue to avoid a race where the polling
            // thread delivers an event between resume() and put(), causing events to be dropped.
            subscribers.computeIfAbsent(correlationId, k -> new ConcurrentHashMap<>())
                .put(subscriberId, consumer);

            if (this.queueSubscriber.isPaused()) {
                this.queueSubscriber.resume();
            }
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

    private boolean isADependency(Execution execution, List<FlowNode> nodes, String correlationId) {
        return execution.getLabels().stream().anyMatch(label -> label.key().equals(CORRELATION_ID) && label.value().equals(correlationId)) &&
            nodes.stream()
                .anyMatch(node -> node.getTenantId().equals(execution.getTenantId()) && node.getNamespace().equals(execution.getNamespace()) && node.getId().equals(execution.getFlowId()));
    }
}
