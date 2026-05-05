package io.kestra.scheduler.pubsub;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.exceptions.NoMatchingWorkerQueueException;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.tasks.WorkerQueueFallback;
import io.kestra.core.worker.WorkerQueues;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerQueueMetaStore;
import io.kestra.core.runners.WorkerQueueRouting;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.runners.WorkerTriggerData;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.services.WorkerQueueService;
import io.kestra.core.utils.Logs;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

//TODO should not be part of the scheduler
@Singleton
public class TriggerWorkerJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(TriggerWorkerJobPublisher.class);

    private final WorkerQueueMetaStore workerQueueMetaStore;
    private final WorkerQueueService workerQueueService;
    private final KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue;

    @Inject
    public TriggerWorkerJobPublisher(
        WorkerQueueMetaStore workerQueueMetaStore,
        WorkerQueueService workerQueueService,
        KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue) {
        this.workerQueueMetaStore = workerQueueMetaStore;
        this.workerQueueService = workerQueueService;
        this.workerJobEventQueue = workerJobEventQueue;
    }

    public void send(TriggerState triggerState, AbstractTrigger trigger, FlowInterface flow, ConditionContext conditionContext) throws InternalException {

        if (log.isDebugEnabled()) {
            Logs.logTrigger(
                triggerState,
                Level.DEBUG,
                "[date: {}] Scheduling evaluation to the worker",
                triggerState.getEvaluatedAt()
            );
        }

        WorkerTrigger workerTrigger = WorkerTrigger
            .builder()
            .trigger(trigger)
            .data(WorkerTriggerData.from(conditionContext, triggerState.context()))
            .build();
        Optional<WorkerQueueRouting> routing;
        try {
            routing = workerQueueService.resolveWorkerQueueForJob(flow, workerTrigger);
        } catch (NoMatchingWorkerQueueException e) {
            // No Worker Queue matches the requested tags — a configuration error.
            // Drop the trigger evaluation with a clear, user-facing log message.
            conditionContext.getRunContext().logger()
                .error("{}, ignoring the trigger.", e.getMessage());
            return;
        }
        try {
            if (routing.isPresent()) {
                String workerQueueId = routing.get().workerQueueId();
                if (WorkerQueues.isDefault(workerQueueId)) {
                    // Explicit default Worker Queue - dispatch without availability check.
                    // The internal dispatch key is null for the default queue (see
                    // WorkerQueues#toDispatchKey).
                    this.workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTrigger, null));
                    return;
                }
                if (workerQueueMetaStore.isWorkerQueueAvailableForId(workerQueueId)) {
                    this.workerJobEventQueue.emit(workerQueueId, WorkerJobEvent.of(workerTrigger, workerQueueId));
                    return;
                }
                // No worker available - apply the routing fallback policy.
                RunContext runContext = conditionContext.getRunContext();
                String workerQueueForLog = WorkerQueues.forLog(routing.get().tags(), workerQueueId);
                WorkerQueueFallback fallback = routing.map(WorkerQueueRouting::fallback).orElse(WorkerQueueFallback.WAIT);
                switch (fallback) {
                    case FAIL -> runContext.logger()
                        .error("No workers are available for {}, ignoring the trigger.", workerQueueForLog);
                    case CANCEL -> runContext.logger()
                        .warn("No workers are available for {}, ignoring the trigger.", workerQueueForLog);
                    case WAIT -> {
                        runContext.logger()
                            .info("No workers are available for {}, waiting for one to be available.", workerQueueForLog);
                        this.workerJobEventQueue.emit(workerQueueId, WorkerJobEvent.of(workerTrigger, workerQueueId));
                    }
                    // IGNORE is a resolution-time directive consumed by the resolver; it
                    // never appears on a resolved routing.
                    case IGNORE -> throw new IllegalStateException(
                        "WorkerQueueFallback.IGNORE must be consumed at resolution time and must not appear on a resolved routing");
                }
            } else {
                // No routing specified - dispatch to the default queue (null key).
                this.workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTrigger, null));
            }
        } catch (QueueException e) {
            log.error("Unable to emit the Worker Trigger job", e);
        }
    }
}
