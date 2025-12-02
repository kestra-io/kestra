package io.kestra.scheduler.pubsub;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.tasks.WorkerGroup;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.utils.Logs;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerGroupMetaStore;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.services.WorkerGroupService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.Optional;

//TODO should not be part of the scheduler
@Singleton
public class TriggerWorkerJobPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(TriggerWorkerJobPublisher.class);
    
    private final WorkerGroupMetaStore workerGroupMetaStore;
    private final WorkerGroupService workerGroupService;
    private final QueueInterface<WorkerJob> workerJobQueue;
    
    @Inject
    public TriggerWorkerJobPublisher(WorkerGroupMetaStore workerGroupMetaStore, WorkerGroupService workerGroupService, QueueInterface<WorkerJob> workerJobQueue) {
        this.workerGroupMetaStore = workerGroupMetaStore;
        this.workerGroupService = workerGroupService;
        this.workerJobQueue = workerJobQueue;
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
        
        // TODO - Do we really need to send all that data ???
        WorkerTrigger workerTrigger = WorkerTrigger
            .builder()
            .trigger(trigger)
            .triggerContext(triggerState.context())
            .conditionContext(conditionContext)
            .build();
        try {
            Optional<WorkerGroup> workerGroup = workerGroupService.resolveGroupFromJob(flow, workerTrigger);
            if (workerGroup.isPresent()) {
                // Check if the worker group exist
                String tenantId = triggerState.getTenantId();
                RunContext runContext = conditionContext.getRunContext();
                String workerGroupKey = runContext.render(workerGroup.get().getKey());
                if (workerGroupMetaStore.isWorkerGroupExistForKey(workerGroupKey, tenantId)) {
                    // Check whether at-least one worker is available
                    if (workerGroupMetaStore.isWorkerGroupAvailableForKey(workerGroupKey)) {
                        this.workerJobQueue.emit(workerGroupKey, workerTrigger);
                    } else {
                        WorkerGroup.Fallback fallback = workerGroup.map(WorkerGroup::getFallback).orElse(WorkerGroup.Fallback.WAIT);
                        switch(fallback) {
                            case FAIL -> runContext.logger()
                                .error("No workers are available for worker group '{}', ignoring the trigger.", workerGroupKey);
                            case CANCEL -> runContext.logger()
                                .warn("No workers are available for worker group '{}', ignoring the trigger.", workerGroupKey);
                            case WAIT -> {runContext.logger()
                                .info("No workers are available for worker group '{}', waiting for one to be available.", workerGroupKey);
                                this.workerJobQueue.emit(workerGroupKey, workerTrigger);
                            }
                        };
                    }
                } else {
                    runContext.logger().error("No worker group exist for key '{}', ignoring the trigger.", workerGroupKey);
                }
            } else {
                this.workerJobQueue.emit(workerTrigger);
            }
        } catch (QueueException e) {
            log.error("Unable to emit the Worker Trigger job", e);
        }
    }
}
