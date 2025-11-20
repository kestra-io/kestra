package io.kestra.executor.handler;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.MultipleConditionEvent;
import io.kestra.executor.FlowTriggerService;
import io.kestra.executor.MessageHandler;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class MultipleConditionEventMessageHandler implements MessageHandler<MultipleConditionEvent> {
    @Inject
    private FlowTriggerService flowTriggerService;

    @Inject
    private MultipleConditionStorageInterface multipleConditionStorage;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Override
    public void handle(MultipleConditionEvent message) {
        flowTriggerService.computeExecutionsFromFlowTriggerPreconditions(message.execution(), message.flow(), multipleConditionStorage)
            .forEach(exec -> {
                try {
                    executionQueue.emit(exec);
                } catch (QueueException e) {
                    log.error("Unable to emit the execution {}", exec.getId(), e);
                }
            });
    }
}
