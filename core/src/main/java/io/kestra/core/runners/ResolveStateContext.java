package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.runners.RunContext;

import java.util.List;

public class ResolveStateContext {
    private final Execution execution;
    private final List<ResolvedTask> tasks;
    private final List<ResolvedTask> errors;
    private final TaskRun parentTaskRun;
    private final RunContext runContext;
    private final boolean allowFailure;

    public ResolveStateContext(Execution execution, List<ResolvedTask> tasks, List<ResolvedTask> errors, TaskRun parentTaskRun, RunContext runContext, boolean allowFailure) {
        this.execution = execution;
        this.tasks = tasks;
        this.errors = errors;
        this.parentTaskRun = parentTaskRun;
        this.runContext = runContext;
        this.allowFailure = allowFailure;
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

    public RunContext getRunContext() {
        return runContext;
    }

    public boolean isAllowFailure() {
        return allowFailure;
    }

    
}
