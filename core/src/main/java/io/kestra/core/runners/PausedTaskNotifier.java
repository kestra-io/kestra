package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.tasks.Task;

import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;

public interface PausedTaskNotifier {

    void taskPaused(FlowInterface flow, Execution execution, TaskRun taskRun, Task task);

    @Singleton
    @Secondary
    class NoopPausedTaskNotifier implements PausedTaskNotifier {
        @Override
        public void taskPaused(FlowInterface flow, Execution execution, TaskRun taskRun, Task task) {
            // no-op
        }
    }
}
