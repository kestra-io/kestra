package io.kestra.core.runners;

import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory for constructing new {@link RunContextLogger} objects.
 */
@Singleton
public class RunContextLoggerFactory {

    private final LogEntryEmitter logEmitter;

    @Inject
    public RunContextLoggerFactory(LogEntryEmitter logEmitter) {
        this.logEmitter = logEmitter;
    }

    public RunContextLogger create(WorkerTask workerTask) {
        return create(workerTask.getTaskRun(), workerTask.getTask(), workerTask.getExecutionKind());
    }

    public RunContextLogger create(TaskRun taskRun, Task task, ExecutionKind executionKind) {
        return new RunContextLogger(
            logEmitter,
            LogEntry.of(taskRun, executionKind),
            task.getLogLevel(),
            task.isLogToFile()
        );
    }

    public RunContextLogger create(Execution execution) {
        return new RunContextLogger(
            logEmitter,
            LogEntry.of(execution),
            null,
            false
        );
    }

    public RunContextLogger create(TriggerContext triggerContext, AbstractTrigger trigger) {
        return new RunContextLogger(
            logEmitter,
            LogEntry.of(triggerContext, trigger),
            trigger.getLogLevel(),
            trigger.isLogToFile()
        );
    }

    public RunContextLogger create(FlowInterface flow, AbstractTrigger trigger) {
        return new RunContextLogger(
            logEmitter,
            LogEntry.of(flow, trigger),
            trigger.getLogLevel(),
            trigger.isLogToFile()
        );
    }
}
