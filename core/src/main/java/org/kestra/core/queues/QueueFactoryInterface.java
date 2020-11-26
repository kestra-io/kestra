package org.kestra.core.queues;

import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.ExecutionKilled;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.runners.WorkerInstance;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.templates.Template;
import org.kestra.core.runners.WorkerTask;
import org.kestra.core.runners.WorkerTaskResult;
import org.kestra.core.runners.WorkerTaskRunning;

public interface QueueFactoryInterface {
    String EXECUTION_NAMED = "executionQueue";
    String WORKERTASK_NAMED = "workerTaskQueue";
    String WORKERTASKRESULT_NAMED = "workerTaskResultQueue";
    String FLOW_NAMED = "flowQueue";
    String TEMPLATE_NAMED = "templateQueue";
    String WORKERTASKLOG_NAMED = "workerTaskLogQueue";
    String KILL_NAMED = "executionKilledQueue";
    String WORKERINSTANCE_NAMED = "workerInstanceQueue";
    String WORKERTASKRUNNING_NAMED = "workerTaskRuninngQueue";
    String TRIGGER_NAMED = "trigger";

    QueueInterface<Execution> execution();

    QueueInterface<WorkerTask> workerTask();

    QueueInterface<WorkerTaskResult> workerTaskResult();

    QueueInterface<LogEntry> logEntry();

    QueueInterface<Flow> flow();

    QueueInterface<ExecutionKilled> kill();

    QueueInterface<Template> template();

    QueueInterface<WorkerInstance> workerInstance();

    QueueInterface<WorkerTaskRunning> workerTaskRunning();

    QueueInterface<Trigger> trigger();

    WorkerTaskQueueInterface workerTaskQueue();
}
