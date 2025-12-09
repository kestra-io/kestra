package io.kestra.core.queues;

import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.*;

public interface QueueFactoryInterface {
    String EXECUTION_NAMED = "executionQueue";
    String EXECUTION_EVENT_NAMED = "executionEventQueue";
    String WORKERJOB_NAMED = "workerJobQueue";
    String WORKERTASKRESULT_NAMED = "workerTaskResultQueue";
    String WORKERTRIGGERRESULT_NAMED = "workerTriggerResultQueue";
    String FLOW_NAMED = "flowQueue";
    String WORKERTASKLOG_NAMED = "workerTaskLogQueue";
    String METRIC_QUEUE = "workerTaskMetricQueue";
    String KILL_NAMED = "executionKilledQueue";
    String WORKERJOBRUNNING_NAMED = "workerJobRunningQueue";
    String SUBFLOWEXECUTIONRESULT_NAMED = "subflowExecutionResultQueue";
    String CLUSTER_EVENT_NAMED = "clusterEventQueue";
    String SUBFLOWEXECUTIONEND_NAMED = "subflowExecutionEndQueue";
    String MULTIPLE_CONDITION_EVENT_NAMED = "multipleConditionEventQueue";
    String EXECUTION_COMMAND_NAMED = "executionCommandQueue";

    QueueInterface<Execution> execution();

    QueueInterface<ExecutionEvent> executionEvent();

    QueueInterface<ExecutionCommand> executionCommand();

    WorkerJobQueueInterface workerJob();

    QueueInterface<WorkerTaskResult> workerTaskResult();

    QueueInterface<WorkerTriggerResult> workerTriggerResult();

    QueueInterface<LogEntry> logEntry();

    QueueInterface<MetricEntry> metricEntry();

    QueueInterface<FlowInterface> flow();

    QueueInterface<ExecutionKilled> kill();

    QueueInterface<WorkerJobRunning> workerJobRunning();

    QueueInterface<SubflowExecutionResult> subflowExecutionResult();

    QueueInterface<SubflowExecutionEnd> subflowExecutionEnd();

    QueueInterface<MultipleConditionEvent> multipleConditionEvent();
}
