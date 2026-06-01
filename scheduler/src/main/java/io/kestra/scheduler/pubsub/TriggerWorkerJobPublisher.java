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
import io.kestra.core.worker.WorkerQueues;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunContext;
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

    private final WorkerQueueService workerQueueService;
    private final KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue;

    @Inject
    public TriggerWorkerJobPublisher(
        WorkerQueueService workerQueueService,
        KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue) {
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
            if (routing.isEmpty() || routing.get().isDefault()) {
                // No routing or explicit default Worker Queue — dispatch with null key.
                this.workerJobEventQueue.emit(null, WorkerJobEvent.of(workerTrigger, null));
                return;
            }
            WorkerQueueRouting r = routing.get();
            String workerQueueId = r.workerQueueId();
            String workerQueueForLog = WorkerQueues.forLog(r.tags(), workerQueueId);
            RunContext runContext = conditionContext.getRunContext();
            switch (r.disposition()) {
                case DISPATCH -> this.workerJobEventQueue.emit(workerQueueId, WorkerJobEvent.of(workerTrigger, workerQueueId));
                case WAIT_AND_DISPATCH -> {
                    runContext.logger()
                        .info("No workers are available for {}, waiting for one to be available.", workerQueueForLog);
                    this.workerJobEventQueue.emit(workerQueueId, WorkerJobEvent.of(workerTrigger, workerQueueId));
                }
                case FAIL -> runContext.logger()
                    .error("No workers are available for {}, ignoring the trigger.", workerQueueForLog);
                case CANCEL -> runContext.logger()
                    .warn("No workers are available for {}, ignoring the trigger.", workerQueueForLog);
            }
        } catch (QueueException e) {
            log.error("Unable to emit the Worker Trigger job", e);
        }
    }
}
