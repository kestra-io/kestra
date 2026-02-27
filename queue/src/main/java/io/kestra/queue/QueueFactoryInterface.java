package io.kestra.queue;

import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.VNodeDispatchQueueInterface;
import io.kestra.core.runners.*;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.runners.MultipleConditionEvent;
import io.kestra.core.runners.SubflowExecutionEnd;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.scheduler.events.SchedulerEvent;
import io.kestra.core.scheduler.events.TriggerEvent;

public interface QueueFactoryInterface {
    DispatchQueueInterface<Execution> executionQueue();

    DispatchQueueInterface<ExecutionCommand> executionCommandQueue();

    DispatchQueueInterface<ExecutionEvent> executionEventQueue();

    BroadcastQueueInterface<ExecutionKilled> killQueue();

    DispatchQueueInterface<SubflowExecutionResult> subflowExecutionResultQueue();

    DispatchQueueInterface<SubflowExecutionEnd> subflowExecutionEndQueue();

    DispatchQueueInterface<MultipleConditionEvent> multipleConditionEventQueue();

    BroadcastQueueInterface<FlowInterface> flowQueue();

    BroadcastQueueInterface<SchedulerEvent> schedulerEventQueue();

    VNodeDispatchQueueInterface<TriggerEvent> triggerEventQueue();

    DispatchQueueInterface<MetricEntry> metricQueue();

    BroadcastQueueInterface<FollowExecutionEvent> followExecutionQueue();

    DispatchQueueInterface<LogEntry> logEntryQueue();

    BroadcastQueueInterface<FollowLogEvent> followLogEventQueue();

    KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue();

    DispatchQueueInterface<WorkerTaskResult> workerTaskResultQueue();
}
