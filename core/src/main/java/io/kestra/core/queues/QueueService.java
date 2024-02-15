package io.kestra.core.queues;

import io.kestra.core.models.Setting;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.runners.*;
import io.kestra.core.server.ServiceInstance;
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
            return ((WorkerInstance) object).getWorkerUuid();
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
        } else if (object.getClass() == SubflowExecution.class) {
            return ((SubflowExecution<?>) object).getExecution().getId();
        } else if (object.getClass() == SubflowExecutionResult.class) {
            return ((SubflowExecutionResult) object).getExecutionId();
        } else if (object.getClass() == ExecutionDelay.class) {
            return ((ExecutionDelay) object).uid();
        } else if (object.getClass() == ExecutorState.class) {
            return ((ExecutorState) object).getExecutionId();
        } else if (object.getClass() == Setting.class) {
            return ((Setting) object).getKey();
        } else if (object.getClass() == Executor.class) {
          return ((Executor) object).getExecution().getId();
        } else if (object.getClass() == FlowTopology.class) {
            return ((FlowTopology) object).uid();
        } else if (object.getClass() == MetricEntry.class) {
            return null;
        } else if (object.getClass() == WorkerTrigger.class) {
            return ((WorkerTrigger) object).getTriggerContext().uid();
        } else if (object.getClass() == WorkerTriggerRunning.class) {
            return ((WorkerTriggerRunning) object).getTriggerContext().uid();
        } else if (object.getClass() == WorkerTriggerResult.class) {
            return ((WorkerTriggerResult) object).getTriggerContext().uid();
        } else if (object.getClass() == ExecutionQueued.class) {
            return ((ExecutionQueued) object).uid();
        } else if (object.getClass() == ServiceInstance.class) {
            return ((ServiceInstance) object).id();
        } else {
            throw new IllegalArgumentException("Unknown type '" + object.getClass().getName() + "'");
        }
    }
}
