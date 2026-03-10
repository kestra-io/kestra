package io.kestra.core.queues;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.runners.*;

// We provide a generic <D> type that allows to pass to each queue bean method the required dependencies to make sure Micronaut dependency tree is built correctly so we have a proper bean shutdown order.
public interface QueueFactoryInterface<D> {
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

    QueueInterface<Execution> execution(D dependencies);

    QueueInterface<Executor> executor(D dependencies);

    WorkerJobQueueInterface workerJob(D dependencies);

    QueueInterface<WorkerTaskResult> workerTaskResult(D dependencies);

    QueueInterface<WorkerTriggerResult> workerTriggerResult(D dependencies);

    QueueInterface<LogEntry> logEntry(D dependencies);

    QueueInterface<MetricEntry> metricEntry(D dependencies);

    QueueInterface<FlowInterface> flow(D dependencies);

    QueueInterface<ExecutionKilled> kill(D dependencies);

    QueueInterface<Template> template(D dependencies);

    QueueInterface<WorkerInstance> workerInstance(D dependencies);

    QueueInterface<WorkerJobRunning> workerJobRunning(D dependencies);

    QueueInterface<Trigger> trigger(D dependencies);

    QueueInterface<SubflowExecutionResult> subflowExecutionResult(D dependencies);

    QueueInterface<SubflowExecutionEnd> subflowExecutionEnd(D dependencies);

    QueueInterface<MultipleConditionEvent> multipleConditionEvent(D dependencies);
}
