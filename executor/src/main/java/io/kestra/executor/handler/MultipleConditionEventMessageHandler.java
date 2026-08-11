package io.kestra.executor.handler;

import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.executions.ExecutionId;
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
    private final FlowTriggerService flowTriggerService;
    private final MultipleConditionStateStore multipleConditionStateStore;
    private final DispatchQueueInterface<ExecutionCommand> executionCommandQueue;

    @Inject
    public MultipleConditionEventMessageHandler(
        FlowTriggerService flowTriggerService,
        MultipleConditionStateStore multipleConditionStateStore,
        DispatchQueueInterface<ExecutionCommand> executionCommandQueue) {
        this.flowTriggerService = flowTriggerService;
        this.multipleConditionStateStore = multipleConditionStateStore;
        this.executionCommandQueue = executionCommandQueue;
    }

    @Override
    public void handle(MultipleConditionEvent message) {
        flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(message.execution(), message.flow(), multipleConditionStateStore)
            .forEach(exec ->
            {
                try {
                    Create cmd = Create.of(new ExecutionId(exec.getTenantId(), exec.getNamespace(), exec.getFlowId(), exec.getId(), exec.getFlowRevision()))
                        .withKind(exec.getKind())
                        .withTrigger(exec.getTrigger())
                        .withLabels(exec.getLabels())
                        .withInputs(exec.getInputs());
                    // Preserve terminal state (e.g. FAILED when trigger input rendering fails).
                    if (exec.getState().isTerminated()) {
                        cmd = cmd.withStateType(exec.getState().getCurrent());
                    }
                    executionCommandQueue.emit(cmd);
                } catch (QueueException e) {
                    log.error("Unable to emit the execution {}", exec.getId(), e);
                }
            });
    }
}
