package io.kestra.core.queues;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.runners.*;

public interface QueueFactoryInterface {
    String EXECUTION_NAMED = "executionQueue";
    String EXECUTION_EVENT_NAMED = "executionEventQueue";
    String WORKERJOB_NAMED = "workerJobQueue";
    String WORKERTASKRESULT_NAMED = "workerTaskResultQueue";
    String WORKERTRIGGERRESULT_NAMED = "workerTriggerResultQueue";
    String WORKERTASKLOG_NAMED = "workerTaskLogQueue";
    String METRIC_QUEUE = "workerTaskMetricQueue";
    String WORKERJOBRUNNING_NAMED = "workerJobRunningQueue";

    QueueInterface<Execution> execution();

    QueueInterface<ExecutionEvent> executionEvent();

    WorkerJobQueueInterface workerJob();

    QueueInterface<WorkerTaskResult> workerTaskResult();

    QueueInterface<WorkerTriggerResult> workerTriggerResult();

    QueueInterface<LogEntry> logEntry();

    QueueInterface<MetricEntry> metricEntry();

    QueueInterface<WorkerJobRunning> workerJobRunning();
}
