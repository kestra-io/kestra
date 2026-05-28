package io.kestra.controller.grpc.services;

import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.worker.MetadataChangePayload;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Subscribes to metastore broadcast queues and forwards each change as a
 * {@link MetadataChangePayload} to a caller-supplied consumer (typically
 * {@link WorkerJobDispatcher#onMetadataChanged(MetadataChangePayload)}).
 * <p>
 * Lifecycle ({@link #start}/{@link #stop}) is owned by {@link WorkerJobDispatcher}; this bean has no
 * Micronaut lifecycle annotations of its own. EE deployments replace this class to add the
 * namespace + tenant broadcast queues alongside the flow queue.
 */
@Singleton
@Slf4j
public class MetadataChangeListener {

    private final BroadcastQueueInterface<FlowInterface> flowQueue;

    private QueueSubscriber<FlowInterface> flowSubscriber;

    @Inject
    public MetadataChangeListener(BroadcastQueueInterface<FlowInterface> flowQueue) {
        this.flowQueue = Objects.requireNonNull(flowQueue);
    }

    public void start(Consumer<MetadataChangePayload> onChange) {
        this.flowSubscriber = flowQueue.subscriber();
        this.flowSubscriber.subscribe(either -> {
            if (either.isRight()) {
                log.warn("Failed to deserialize flow change: {}", either.getRight().getMessage());
                return;
            }
            FlowInterface flow = either.getLeft();
            onChange.accept(new MetadataChangePayload(
                MetadataChangePayload.Type.FLOW, flow.getTenantId(), flow.getNamespace()));
        });
    }

    public void stop() {
        closeQuietly(flowSubscriber);
    }

    protected void closeQuietly(QueueSubscriber<?> subscriber) {
        if (subscriber == null) {
            return;
        }
        try {
            subscriber.close();
        } catch (Exception e) {
            log.warn("Error closing metadata-change subscriber: {}", e.getMessage());
        }
    }
}
