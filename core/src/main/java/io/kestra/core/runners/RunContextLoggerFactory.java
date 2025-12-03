package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Factory for constructing new {@link RunContextLogger} objects.
 */
@Singleton
public class RunContextLoggerFactory {

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    public RunContextLogger create(WorkerTask workerTask) {
        return create(workerTask.getTaskRun(), workerTask.getTask(), workerTask.getExecutionKind());
    }

    public RunContextLogger create(TaskRun taskRun, Task task, ExecutionKind executionKind) {
        return new RunContextLogger(
            logQueue,
            LogEntry.of(taskRun, executionKind),
            task.getLogLevel(),
            task.isLogToFile()
        );
    }

    public RunContextLogger create(Execution execution) {
        return new RunContextLogger(
            logQueue,
            LogEntry.of(execution),
            null,
            false
        );
    }

    public RunContextLogger create(TriggerContext triggerContext, AbstractTrigger trigger) {
        return new RunContextLogger(
            logQueue,
            LogEntry.of(triggerContext, trigger),
            trigger.getLogLevel(),
            trigger.isLogToFile()
        );
    }

    public RunContextLogger create(FlowInterface flow, AbstractTrigger trigger) {
        return new RunContextLogger(
            logQueue,
            LogEntry.of(flow, trigger),
            trigger.getLogLevel(),
            trigger.isLogToFile()
        );
    }
}
