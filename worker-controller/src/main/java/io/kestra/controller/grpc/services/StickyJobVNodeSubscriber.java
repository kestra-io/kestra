package io.kestra.controller.grpc.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerJobEvent.StickyRouting;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.utils.Disposable;
import io.kestra.core.utils.Either;
import io.kestra.core.vnodes.ControllerVNodesAssignmentChannel;
import io.kestra.core.vnodes.VNodesAssigner;
import io.kestra.core.vnodes.internals.DefaultVNodesAssigner;
import io.kestra.core.worker.StickyWorkerRouting;
import io.kestra.core.worker.StickyWorkerRouting.WorkerLocation;

import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Subscribes this controller to the vNodes the liveness coordinator assigns it, and dispatches the sticky jobs received
 * on them, forwarding the ones whose worker is held by another controller.
 */
@Singleton
@Slf4j
@Requires(beans = ServiceLivenessStore.class)
public class StickyJobVNodeSubscriber implements WorkerLifecycleListener {

    private final KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue;
    private final StickyWorkerRouting routing;
    private final Duration maxWorkerUnknownDuration;
    private final DefaultVNodesAssigner vNodesAssigner;
    private final Map<Integer, QueueSubscriber<WorkerJobEvent>> subscribers = new ConcurrentHashMap<>();
    private final Map<Integer, BusyWorker> pausedVNodes = new ConcurrentHashMap<>();

    private volatile WorkerJobDispatcher dispatcher;
    private volatile Disposable assignment;

    @Inject
    public StickyJobVNodeSubscriber(KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue,
        StickyWorkerRouting routing,
        ControllerVNodesAssignmentChannel channel,
        ServerConfig serverConfig) {
        this.workerJobEventQueue = workerJobEventQueue;
        this.routing = routing;
        // by then the liveness coordinator has declared a worker lost, so a running worker no controller holds reveals a routing bug
        this.maxWorkerUnknownDuration = serverConfig.liveness().timeout().plus(serverConfig.liveness().interval()).multipliedBy(2);
        this.vNodesAssigner = new DefaultVNodesAssigner(channel);
    }

    @Override
    public void init(WorkerJobDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        vNodesAssigner.start();
    }

    /**
     * Joins the vNodes assignment as the given controller.
     */
    public void start(String controllerId) {
        assignment = vNodesAssigner.subscribe(controllerId, new VNodesAssigner.VNodeAssignmentListener() {
            @Override
            public void onVNodesRevoked() {
                subscribers.keySet().stream().toList().forEach(vNode -> closeQuietly(vNode, subscribers.remove(vNode)));
                pausedVNodes.clear();
            }

            @Override
            public void onVNodesAssigned(Set<Integer> vNodes) {
                vNodes.forEach(vNode -> subscribers.computeIfAbsent(vNode, StickyJobVNodeSubscriber.this::subscribe));
            }
        });
    }

    @Override
    public void onWorkerCapacityChanged(WorkerStreamContext<?> context) {
        resumeVNodesWaitingFor(context.getWorkerId());
    }

    @Override
    public void onWorkerUnregistered(WorkerStreamContext<?> context) {
        resumeVNodesWaitingFor(context.getWorkerId());
    }

    /**
     * @return the vNodes this controller is assigned, published with its liveness.
     */
    public Set<Integer> assignedVNodes() {
        return Set.copyOf(subscribers.keySet());
    }

    private QueueSubscriber<WorkerJobEvent> subscribe(int vNode) {
        QueueSubscriber<WorkerJobEvent> subscriber = workerJobEventQueue.subscriber(StickyWorkerRouting.routingKey(vNode));
        subscriber.subscribe(either -> handle(vNode, either));
        log.info("Subscribed to vNode {} for sticky jobs", vNode);
        return subscriber;
    }

    private void handle(int vNode, Either<WorkerJobEvent, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Deserialization error for a sticky job: {}", either.getRight().getMessage());
            return;
        }
        WorkerJobEvent event = either.getLeft();
        switch (dispatcher.dispatchSticky(event)) {
            case WORKER_BUSY -> pause(vNode, new BusyWorker(event.stickyRouting().workerId(), event.workerQueueId()));
            case WORKER_NOT_CONNECTED -> forward(event);
            case HANDLED -> {
            }
        }
    }

    private void pause(int vNode, BusyWorker busyWorker) {
        QueueSubscriber<WorkerJobEvent> subscriber = subscribers.get(vNode);
        if (subscriber == null) {
            return;
        }
        // paused before being recorded, so a resume can never run before the pause it undoes
        subscriber.pause();
        pausedVNodes.put(vNode, busyWorker);
        log.debug("Paused vNode {} until worker '{}' has a free permit", vNode, busyWorker.workerId());
        // the permit may have been freed before the vNode was recorded, and then no other notification comes
        resumeIfFree(vNode);
    }

    private void resumeVNodesWaitingFor(String workerId) {
        pausedVNodes.forEach((vNode, busyWorker) ->
        {
            if (busyWorker.workerId().equals(workerId)) {
                resumeIfFree(vNode);
            }
        });
    }

    private void resumeIfFree(int vNode) {
        BusyWorker busyWorker = pausedVNodes.get(vNode);
        if (busyWorker == null || dispatcher.isWorkerBusy(busyWorker.workerId(), busyWorker.workerQueueId()) || !pausedVNodes.remove(vNode, busyWorker)) {
            return;
        }
        QueueSubscriber<WorkerJobEvent> subscriber = subscribers.get(vNode);
        if (subscriber != null) {
            subscriber.resume();
            log.debug("Resumed vNode {}", vNode);
        }
    }

    private void forward(WorkerJobEvent event) {
        StickyRouting stickyRouting = event.stickyRouting();
        try {
            switch (routing.locate(stickyRouting.workerId())) {
                case WorkerLocation.Held held -> emit(event.withStickyRouting(stickyRouting.workerFound(held.routingKey())));
                case WorkerLocation.Unknown unknown -> retryOrFail(event);
                case WorkerLocation.Lost lost -> log.warn(
                    "Dropping the job {}: its worker '{}' is no longer running, so the liveness coordinator recovers it.",
                    event.job().uid(), stickyRouting.workerId()
                );
            }
        } catch (QueueException e) {
            log.error("Failed to forward the sticky job {}: {}", event.job().uid(), e.getMessage(), e);
        }
    }

    private void retryOrFail(WorkerJobEvent event) throws QueueException {
        StickyRouting stickyRouting = event.stickyRouting().workerUnknown(Instant.now());
        if (stickyRouting.workerUnknownSince().plus(maxWorkerUnknownDuration).isAfter(Instant.now())) {
            emit(event.withStickyRouting(stickyRouting));
            return;
        }
        log.error(
            "Failing the job {}: its worker '{}' is running but no controller has held it since {}.",
            event.job().uid(), stickyRouting.workerId(), stickyRouting.workerUnknownSince()
        );
        dispatcher.failSticky(event);
    }

    private void emit(WorkerJobEvent event) throws QueueException {
        workerJobEventQueue.emit(event.key(), event);
    }

    private void closeQuietly(int vNode, QueueSubscriber<WorkerJobEvent> subscriber) {
        try {
            subscriber.close();
            log.info("Unsubscribed from vNode {} for sticky jobs", vNode);
        } catch (Exception e) {
            log.warn("Error closing the subscription to vNode {}: {}", vNode, e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        if (assignment != null) {
            assignment.dispose();
        }
        vNodesAssigner.stop();
        subscribers.forEach(this::closeQuietly);
        subscribers.clear();
        pausedVNodes.clear();
    }

    private record BusyWorker(String workerId, String workerQueueId) {
    }
}
