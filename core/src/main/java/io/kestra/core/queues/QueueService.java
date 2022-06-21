package io.kestra.core.queues;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.runners.*;

import jakarta.inject.Singleton;

@Singleton
public class QueueService {
    public String key(Object object) {
        if (object.getClass() == Execution.class) {
            return ((Execution) object).getId();
        } else if (object.getClass() == WorkerTask.class) {
            return ((WorkerTask) object).getTaskRun().getId();
        } else if (object.getClass() == WorkerTaskRunning.class) {
            return ((WorkerTaskRunning) object).getTaskRun().getId();
        } else if (object.getClass() == WorkerInstance.class) {
            return ((WorkerInstance) object).getWorkerUuid().toString();
        } else if (object.getClass() == WorkerTaskResult.class) {
            return ((WorkerTaskResult) object).getTaskRun().getId();
        } else if (object.getClass() == LogEntry.class) {
            return null;
        } else if (object.getClass() == Flow.class) {
            return ((Flow) object).uid();
        } else if (object.getClass() == Template.class) {
            return ((Template) object).uid();
        } else if (object.getClass() == ExecutionKilled.class) {
            return ((ExecutionKilled) object).getExecutionId();
        } else if (object.getClass() == Trigger.class) {
            return ((Trigger) object).uid();
        } else if (object.getClass() == MultipleConditionWindow.class) {
            return ((MultipleConditionWindow) object).uid();
        } else if (object.getClass() == WorkerTaskExecution.class) {
            return ((WorkerTaskExecution) object).getExecution().getId();
        } else if (object.getClass() == ExecutionDelay.class) {
            return ((ExecutionDelay) object).getExecutionId();
        } else if (object.getClass() == ExecutorState.class) {
            return ((ExecutorState) object).getExecutionId();
        } else {
            throw new IllegalArgumentException("Unknown type '" + object.getClass().getName() + "'");
        }
    }
}
