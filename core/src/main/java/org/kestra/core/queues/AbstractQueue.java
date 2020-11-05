package org.kestra.core.queues;

import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.ExecutionKilled;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.templates.Template;
import org.kestra.core.runners.WorkerInstance;
import org.kestra.core.runners.WorkerTask;
import org.kestra.core.runners.WorkerTaskResult;
import org.kestra.core.runners.WorkerTaskRunning;

public abstract class AbstractQueue {
    public static String key(Object object) {
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
        } else {
            throw new IllegalArgumentException("Unknown type '" + object.getClass().getName() + "'");
        }
    }
}
