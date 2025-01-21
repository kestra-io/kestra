package io.kestra.webserver.services;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ConditionService;
import io.micronaut.http.sse.Event;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.FluxSink;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
@Slf4j
@Singleton
public class ExecutionStreamingService {
    private final Map<String, Map<String, FluxSink<Event<Execution>>>> subscribers = new ConcurrentHashMap<>();

    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private final QueueInterface<Execution> executionQueue;
    private final FlowRepositoryInterface flowRepository;
    private final ConditionService conditionService;
    private Runnable queueConsumer;
    @Inject
    public ExecutionStreamingService(
        QueueInterface<Execution> executionQueue,
        FlowRepositoryInterface flowRepository,
        ConditionService conditionService
    ) {
        this.executionQueue = executionQueue;
        this.flowRepository = flowRepository;
        this.conditionService = conditionService;
    }

    @PostConstruct
    private void startQueueConsumer() {
        // Single queue consumer
        this.queueConsumer = executionQueue.receive(either -> {
            if (either.isRight()) {
                log.error("Unable to deserialize execution: {}", either.getRight().getMessage());
                return;
            }

            Execution execution = either.getLeft();
            String executionId = execution.getId();

            // Get all subscribers for this execution
            Map<String, FluxSink<Event<Execution>>> executionSubscribers = subscribers.get(executionId);

            if (executionSubscribers != null && !executionSubscribers.isEmpty()) {
                executionSubscribers.values().forEach(sink -> {
                    try {
                        sink.next(Event.of(execution).id("progress"));

                        Flow flow = flowRepository.findByExecutionWithoutAcl(execution);
                        if (isStopFollow(flow, execution)) {
                            sink.next(Event.of(execution).id("end"));
                            sink.complete();
                        }
                    } catch (Exception e) {
                        log.error("Error sending execution update", e);
                        sink.error(e);
                    }
                });
            }
        });
    }

    public void registerSubscriber(String executionId, FluxSink<Event<Execution>> sink) {
        String subscriberId = UUID.randomUUID().toString();

        subscribers.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>())
            .put(subscriberId, sink);

        // Cleanup when subscriber disconnects
        sink.onCancel(() -> unregisterSubscriber(executionId, subscriberId));
    }

    public void unregisterSubscriber(String executionId, String subscriberId) {
        Map<String, FluxSink<Event<Execution>>> executionSubscribers = subscribers.get(executionId);
        if (executionSubscribers != null) {
            executionSubscribers.remove(subscriberId);
            if (executionSubscribers.isEmpty()) {
                subscribers.remove(executionId);
            }
        }
    }

    private boolean isStopFollow(Flow flow, Execution execution) {
        return conditionService.isTerminatedWithListeners(flow, execution) &&
            execution.getState().getCurrent() != State.Type.PAUSED;
    }

    @PreDestroy
    public void shutdown() {
        if (queueConsumer != null) {
            queueConsumer.run();
        }
    }
}