package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory for constructing new {@link RunContextLogger} objects.
 */
@Singleton
public class RunContextLoggerFactory {

    @Inject
    protected ApplicationContext applicationContext;

    public RunContextLogger create(TaskRun taskRun, Task task) {
        return new RunContextLogger(
            getLogQueue(),
            LogEntry.of(taskRun),
            task.getLogLevel()
        );
    }

    public RunContextLogger create(Execution execution) {
        return new RunContextLogger(
            getLogQueue(),
            LogEntry.of(execution),
            null
        );
    }

    public RunContextLogger create(TriggerContext triggerContext, AbstractTrigger trigger) {
        return new RunContextLogger(
            getLogQueue(),
            LogEntry.of(triggerContext, trigger),
            trigger.getLogLevel()
        );
    }

    public RunContextLogger create(Flow flow, AbstractTrigger trigger) {
        return new RunContextLogger(
            getLogQueue(),
            LogEntry.of(flow, trigger),
            trigger.getLogLevel()
        );
    }
    @SuppressWarnings("unchecked")
    private QueueInterface<LogEntry> getLogQueue() {
        return applicationContext.findBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.WORKERTASKLOG_NAMED)
        ).orElseThrow();
    }
}
