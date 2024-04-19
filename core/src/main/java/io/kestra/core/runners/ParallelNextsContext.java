package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.ResolvedTask;

import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class ParallelNextsContext {
    private final Execution execution;
    private final List<ResolvedTask> tasks;
    private final List<ResolvedTask> errors;
    private final TaskRun parentTaskRun;
    private final Integer concurrency;
    private final BiFunction<Stream<NextTaskRun>, List<TaskRun>, Stream<NextTaskRun>> nextTaskRunFunction;

    public ParallelNextsContext(Execution execution, List<ResolvedTask> tasks, List<ResolvedTask> errors, TaskRun parentTaskRun, Integer concurrency, BiFunction<Stream<NextTaskRun>, List<TaskRun>, Stream<NextTaskRun>> nextTaskRunFunction) {
        this.execution = execution;
        this.tasks = tasks;
        this.errors = errors;
        this.parentTaskRun = parentTaskRun;
        this.concurrency = concurrency;
        this.nextTaskRunFunction = nextTaskRunFunction;
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

    public Integer getConcurrency() {
        return concurrency;
    }

    public BiFunction<Stream<NextTaskRun>, List<TaskRun>, Stream<NextTaskRun>> getNextTaskRunFunction() {
        return nextTaskRunFunction;
    }

   
}
