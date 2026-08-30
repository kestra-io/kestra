package io.kestra.executor.handler;

import java.time.DateTimeException;
import java.util.List;

import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionId;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStore;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.MultipleConditionEvent;
import io.kestra.core.scheduler.events.UnscheduledTriggerFired;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
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
    private final TriggerEventQueue triggerEventQueue;

    @Inject
    public MultipleConditionEventMessageHandler(
        FlowTriggerService flowTriggerService,
        MultipleConditionStateStore multipleConditionStateStore,
        DispatchQueueInterface<ExecutionCommand> executionCommandQueue,
        TriggerEventQueue triggerEventQueue) {
        this.flowTriggerService = flowTriggerService;
        this.multipleConditionStateStore = multipleConditionStateStore;
        this.executionCommandQueue = executionCommandQueue;
        this.triggerEventQueue = triggerEventQueue;
    }

    @Override
    public void handle(MultipleConditionEvent message) {
        final List<Execution> executions;
        try {
            executions = flowTriggerService.computeExecutionsFromFlowTriggerDependsOn(message.execution(), message.flow(), multipleConditionStateStore);
        } catch (DateTimeException | ArithmeticException e) {
            log.error("Skipping flow-trigger evaluation for flow '{}': the trigger window is out of the supported range.", message.flow().getId(), e);
            return;
        }

        executions
            .forEach(exec ->
            {
                try {
                    Create cmd = Create.of(new ExecutionId(exec.getTenantId(), exec.getNamespace(), exec.getFlowId(), exec.getId(), exec.getFlowRevision()))
                        .withKind(exec.getKind())
                        .withTrigger(exec.getTrigger())
                        .withLabels(exec.getLabels())
                        .withInputs(exec.getInputs())
                        .withExecutionDepth(exec.getMetadata().getExecutionDepth());
                    // Preserve terminal state (e.g. FAILED when trigger input rendering fails).
                    if (exec.getState().isTerminated()) {
                        cmd = cmd.withStateType(exec.getState().getCurrent());
                    }
                    executionCommandQueue.emit(cmd);
                    triggerEventQueue.send(UnscheduledTriggerFired.of(exec));
                } catch (QueueException e) {
                    log.error("Unable to emit the execution {}", exec.getId(), e);
                }
            });
    }
}
