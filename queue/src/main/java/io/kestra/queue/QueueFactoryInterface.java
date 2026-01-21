package io.kestra.queue;

import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.MultipleConditionEvent;
import io.kestra.core.runners.SubflowExecutionEnd;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.SubflowExecutionResult;

public interface QueueFactoryInterface {
    DispatchQueueInterface<ExecutionCommand> executionCommandQueue();

    BroadcastQueueInterface<ExecutionKilled> killQueue();

    DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue();

    DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue();

    DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue();

    DispatchQueueInterface<FlowInterface> flowQueue();
}
