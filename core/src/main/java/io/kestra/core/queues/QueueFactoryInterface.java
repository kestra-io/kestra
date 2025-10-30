package io.kestra.core.queues;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.runners.*;

public interface QueueFactoryInterface {
    String EXECUTION_NAMED = "executionQueue";
    String EXECUTOR_NAMED = "executorQueue";
    String WORKERJOB_NAMED = "workerJobQueue";
    String WORKERTASKRESULT_NAMED = "workerTaskResultQueue";
    String WORKERTRIGGERRESULT_NAMED = "workerTriggerResultQueue";
    String FLOW_NAMED = "flowQueue";
    String TEMPLATE_NAMED = "templateQueue";
    String WORKERTASKLOG_NAMED = "workerTaskLogQueue";
    String METRIC_QUEUE = "workerTaskMetricQueue";
    String KILL_NAMED = "executionKilledQueue";
    String WORKERINSTANCE_NAMED = "workerInstanceQueue";
    String WORKERJOBRUNNING_NAMED = "workerJobRunningQueue";
    String TRIGGER_NAMED = "triggerQueue";
    String SUBFLOWEXECUTIONRESULT_NAMED = "subflowExecutionResultQueue";
    String CLUSTER_EVENT_NAMED = "clusterEventQueue";
    String SUBFLOWEXECUTIONEND_NAMED = "subflowExecutionEndQueue";
    String MULTIPLE_CONDITION_EVENT_NAMED = "multipleConditionEventQueue";

    QueueInterface<Execution> execution();

    QueueInterface<Executor> executor();

    WorkerJobQueueInterface workerJob();

    QueueInterface<WorkerTaskResult> workerTaskResult();

    QueueInterface<WorkerTriggerResult> workerTriggerResult();

    QueueInterface<LogEntry> logEntry();

    QueueInterface<MetricEntry> metricEntry();

    QueueInterface<FlowInterface> flow();

    QueueInterface<ExecutionKilled> kill();

    QueueInterface<Template> template();

    QueueInterface<WorkerInstance> workerInstance();

    QueueInterface<WorkerJobRunning> workerJobRunning();

    QueueInterface<Trigger> trigger();

    QueueInterface<SubflowExecutionResult> subflowExecutionResult();

    QueueInterface<SubflowExecutionEnd> subflowExecutionEnd();

    QueueInterface<MultipleConditionEvent> multipleConditionEvent();
}
