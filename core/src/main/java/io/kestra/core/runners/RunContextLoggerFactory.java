package io.kestra.core.runners;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.event.Level;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.runners.configuration.LoggingConfiguration;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.MapUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory for constructing new {@link RunContextLogger} objects.
 */
@Singleton
public class RunContextLoggerFactory {

    private final LogEntryEmitter logEmitter;
    private final LoggingConfiguration loggingConfiguration;

    @Inject
    public RunContextLoggerFactory(LogEntryEmitter logEmitter, LoggingConfiguration loggingConfiguration) {
        this.logEmitter = logEmitter;
        this.loggingConfiguration = loggingConfiguration;
    }

    public RunContextLogger create(WorkerTask workerTask) {
        Task task = workerTask.getTask();
        return new RunContextLogger(
            logEmitter,
            LogEntry.of(workerTask.getTaskRun(), workerTask.getExecutionKind()),
            task.getLogLevel(),
            task.isLogToFile(),
            mdcLabels(workerLabels(workerTask))
        );
    }

    public RunContextLogger create(WorkerTaskResult workerTaskResult) {
        // TODO this is not ideal but this is the best we can do for now here
        return new RunContextLogger(
            logEmitter,
            LogEntry.of(workerTaskResult.getTaskRun(), null),
            Level.TRACE,
            false
        );
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
            false,
            mdcLabels(Label.toMap(execution.getLabels()))
        );
    }

    public RunContextLogger create(TriggerId triggerId, AbstractTrigger trigger) {
        return new RunContextLogger(
            logEmitter,
            LogEntry.of(triggerId, trigger),
            trigger.getLogLevel(),
            trigger.isLogToFile()
        );
    }

    public RunContextLogger create(FlowId flow, AbstractTrigger trigger) {
        return new RunContextLogger(
            logEmitter,
            LogEntry.of(flow, trigger),
            trigger.getLogLevel(),
            trigger.isLogToFile()
        );
    }

    /**
     * Filters the given labels to the keys allow-listed by {@code kestra.logging.labels}.
     */
    private Map<String, String> mdcLabels(Map<String, String> labels) {
        final List<String> configuredKeys = loggingConfiguration.labels();
        if (ListUtils.isEmpty(configuredKeys) || MapUtils.isEmpty(labels)) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (String key : configuredKeys) {
            String value = labels.get(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * Reads the execution labels already carried in the worker task variables, to avoid a heavier worker message.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> workerLabels(WorkerTask workerTask) {
        Object labels = workerTask.getData().variables().get(RunVariables.LABELS);
        if (!(labels instanceof Map<?, ?> nested)) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();
        MapUtils.nestedToFlattenMap((Map<String, Object>) nested)
            .forEach((key, value) -> result.put(key, value == null ? null : value.toString()));
        return result;
    }
}
