package org.floworc.core.models.tasks;

import org.floworc.core.models.executions.Execution;
import org.floworc.core.runners.RunContext;

import java.util.List;
import java.util.Optional;

public interface FlowableTask {
    List<Task> childTasks();

    List<Task> getErrors();

    FlowableResult nexts(RunContext runContext, Execution execution);
}
