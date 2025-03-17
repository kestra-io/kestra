package io.kestra.core.queues;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.Setting;
import io.kestra.core.models.executions.*;
import io.kestra.core.runners.*;
import jakarta.inject.Singleton;

@Singleton
public class QueueService {
    public String key(Object object) {
        if (object instanceof HasUID hasUID) {
            return hasUID.uid();
        } else if (object.getClass() == Execution.class) {
            return ((Execution) object).getId();
        } else if (object.getClass() == LogEntry.class) {
            return null;
        } else if (object.getClass() == SubflowExecution.class) {
            return ((SubflowExecution<?>) object).getExecution().getId();
        } else if (object.getClass() == SubflowExecutionResult.class) {
            return ((SubflowExecutionResult) object).getExecutionId();
        } else if (object.getClass() == ExecutorState.class) {
            return ((ExecutorState) object).getExecutionId();
        } else if (object.getClass() == Setting.class) {
            return ((Setting) object).getKey();
        } else if (object.getClass() == Executor.class) {
            return ((Executor) object).getExecution().getId();
        } else if (object.getClass() == MetricEntry.class) {
            return null;
        } else if (object.getClass() == ExecutionRunning.class) {
            return ((ExecutionRunning) object).getExecution().getId();
        } else if (object.getClass() == SubflowExecutionEnd.class) {
            return ((SubflowExecutionEnd) object).getParentExecutionId();
        } else {
            throw new IllegalArgumentException("Unknown type '" + object.getClass().getName() + "'");
        }
    }
}
