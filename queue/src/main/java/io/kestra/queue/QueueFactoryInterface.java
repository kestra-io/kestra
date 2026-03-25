package io.kestra.queue;

import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.core.runners.*;
import io.kestra.core.runners.MultipleConditionEvent;
import io.kestra.core.runners.SubflowExecutionEnd;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.scheduler.events.SchedulerEvent;
import io.kestra.core.scheduler.events.TriggerEvent;

public interface QueueFactoryInterface<D> {
    DispatchQueueInterface<Execution> executionQueue(D dependencies);

    DispatchQueueInterface<ExecutionCommand> executionCommandQueue(D dependencies);

    DispatchQueueInterface<ExecutionEvent> executionEventQueue(D dependencies);

    BroadcastQueueInterface<ExecutionKilled> killQueue(D dependencies);

    DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue(D dependencies);

    DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue(D dependencies);

    DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue(D dependencies);

    BroadcastQueueInterface<FlowInterface> flowQueue(D dependencies);

    BroadcastQueueInterface<SchedulerEvent> schedulerEventQueue(D dependencies);

    VNodeDispatchQueueInterface<TriggerEvent> triggerEventQueue(D dependencies);

    DispatchQueueInterface<MetricEntry> metricQueue(D dependencies);

    BroadcastQueueInterface<FollowExecutionEvent> followExecutionQueue(D dependencies);

    DispatchQueueInterface<LogEntry> logEntryQueue(D dependencies);

    BroadcastQueueInterface<FollowLogEvent> followLogEventQueue(D dependencies);

    KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue(D dependencies);

    DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue(D dependencies);
}
