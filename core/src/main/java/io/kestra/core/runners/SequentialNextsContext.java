package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.ResolvedTask;

import java.util.List;

public class SequentialNextsContext {
    public final Execution execution;
    private final List<ResolvedTask> tasks;
    private final List<ResolvedTask> errors;
    private final TaskRun parentTaskRun;

    public SequentialNextsContext(Execution execution, List<ResolvedTask> tasks, List<ResolvedTask> errors, TaskRun parentTaskRun) {
        this.execution = execution;
        this.tasks = tasks;
        this.errors = errors;
        this.parentTaskRun = parentTaskRun;
    }

    public Execution getExecution() {
        return execution;
    }

    public List<ResolvedTask> getTasks() {
        return tasks;
    }

    public List<ResolvedTask> getErrors() {
        return errors;
    }

    public TaskRun getParentTaskRun() {
        return parentTaskRun;
    }

    
}
