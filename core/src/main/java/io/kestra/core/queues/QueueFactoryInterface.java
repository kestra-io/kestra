package io.kestra.core.queues;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.runners.*;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.templates.Template;

public interface QueueFactoryInterface {
    String EXECUTION_NAMED = "executionQueue";
    String EXECUTOR_NAMED = "executorQueue";
    String WORKERTASK_NAMED = "workerTaskQueue";
    String WORKERTASKRESULT_NAMED = "workerTaskResultQueue";
    String FLOW_NAMED = "flowQueue";
    String TEMPLATE_NAMED = "templateQueue";
    String WORKERTASKLOG_NAMED = "workerTaskLogQueue";
    String METRIC_QUEUE = "workerTaskMetricQueue";
    String KILL_NAMED = "executionKilledQueue";
    String WORKERINSTANCE_NAMED = "workerInstanceQueue";
    String WORKERTASKRUNNING_NAMED = "workerTaskRuninngQueue";
    String TRIGGER_NAMED = "triggerQueue";

    QueueInterface<Execution> execution();

    QueueInterface<Executor> executor();

    QueueInterface<WorkerTask> workerTask();

    QueueInterface<WorkerTaskResult> workerTaskResult();

    QueueInterface<LogEntry> logEntry();

    QueueInterface<MetricEntry> metricEntry();

    QueueInterface<Flow> flow();

    QueueInterface<ExecutionKilled> kill();

    QueueInterface<Template> template();

    QueueInterface<WorkerInstance> workerInstance();

    QueueInterface<WorkerTaskRunning> workerTaskRunning();

    QueueInterface<Trigger> trigger();

    WorkerTaskQueueInterface workerTaskQueue();

}
