package io.kestra.executor.handler;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStore;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.MultipleConditionEvent;
import io.kestra.executor.FlowTriggerService;
import io.kestra.executor.MessageHandler;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class MultipleConditionEventMessageHandler implements MessageHandler<MultipleConditionEvent> {
    @Inject
    private FlowTriggerService flowTriggerService;

    @Inject
    private MultipleConditionStateStore multipleConditionStateStore;

    @Inject
    private DispatchQueueInterface<Execution> executionQueue;

    @Override
    public void handle(MultipleConditionEvent message) {
        flowTriggerService.computeExecutionsFromFlowTriggerPreconditions(message.execution(), message.flow(), multipleConditionStateStore)
            .forEach(exec ->
            {
                try {
                    executionQueue.emit(exec);
                } catch (QueueException e) {
                    log.error("Unable to emit the execution {}", exec.getId(), e);
                }
            });
    }
}
