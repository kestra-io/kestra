package io.kestra.scheduler.pubsub;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.Scheduler;
import io.kestra.core.runners.WorkerTriggerResult;
import io.kestra.core.scheduler.TriggerEventQueue;
import io.kestra.core.scheduler.events.TriggerEvaluated;
import io.kestra.core.utils.Disposable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO - Temporary service that will be replaced by the worker controller.
 */
@Singleton
public class TriggerWorkerJobResultSubscriber {
    
    private static final Logger LOG = LoggerFactory.getLogger(TriggerWorkerJobResultSubscriber.class);
    
    // Services
    private final TriggerExecutionPublisher triggerExecutionSender;
    
    // Queues
    private final QueueInterface<WorkerTriggerResult> workerTriggerResultQueue;
    private final TriggerEventQueue triggerEventQueue;
    
    /**
     * Creates a new {@link TriggerWorkerJobResultSubscriber} instance.
     *
     * @param workerTriggerResultQueue The WorkerTriggerResult queue.
     */
    public TriggerWorkerJobResultSubscriber(final QueueInterface<WorkerTriggerResult> workerTriggerResultQueue,
                                            final TriggerEventQueue triggerEventQueue,
                                            final TriggerExecutionPublisher triggerExecutionSender) {
        this.workerTriggerResultQueue = workerTriggerResultQueue;
        this.triggerExecutionSender = triggerExecutionSender;
        this.triggerEventQueue = triggerEventQueue;
    }
    
    public Disposable subscribe() {
        return Disposable.of(this.workerTriggerResultQueue.receive(
            null,
            Scheduler.class,
            either -> {
                if (either.isRight()) {
                    LOG.error("Unable to deserialize a worker trigger result: {}", either.getRight().getMessage());
                    return;
                }
                
                WorkerTriggerResult workerTriggerResult = either.getLeft();
                TriggerContext triggerContext = workerTriggerResult.getTriggerContext();
                
                // Get if an Execution is attached to the TriggerResult.
                Execution execution = workerTriggerResult.getExecution().orElse(null);
                if (execution != null) {
                    execution = execution.withTenantId(triggerContext.getTenantId());
                }
                
                AbstractTrigger trigger = workerTriggerResult.getTrigger();
                if (trigger instanceof PollingTriggerInterface) {
                    triggerEventQueue.send(new TriggerEvaluated(TriggerId.of(triggerContext), execution));
                }
                else if (trigger instanceof RealtimeTriggerInterface && execution != null) {
                    triggerExecutionSender.send(execution);
                }
            }
        ));
    }
}
