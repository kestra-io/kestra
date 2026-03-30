package io.kestra.executor;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionDelay;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.SubflowExecution;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.core.runners.WorkerTask;

import lombok.Getter;

@Getter
public class ExecutorContext {

    /**
     * Executor-local wrapper that pairs a {@link WorkerTask} (wire model) with its
     * {@link RunContext} (needed for executor-side rendering like {@code runIf} and worker group keys).
     * The RunContext does NOT travel to the worker — only the WorkerTask does.
     */
    public record ExecutorWorkerTask(WorkerTask workerTask, RunContext runContext) {
    }

    private Execution execution;
    private Exception exception;
    private final List<String> from = new ArrayList<>();
    private boolean executionUpdated = false;
    private FlowWithSource flow;
    // We usually only use one of the lists above, not many in each processing loop.
    // And as we always use addAll the capacity of the list will grow to what's needed when used,
    // so we initialize them with 0 to save memory.
    private final List<TaskRun> nexts = new ArrayList<>(0);
    private final List<ExecutorWorkerTask> workerTasks = new ArrayList<>(0);
    private final List<ExecutionDelay> executionDelays = new ArrayList<>(0);
    private final List<SubflowExecution<?>> subflowExecutions = new ArrayList<>(0);
    private final List<SubflowExecutionResult> subflowExecutionResults = new ArrayList<>(0);
    private State.Type originalState;

    public ExecutorContext(Execution execution) {
        this.execution = execution;
        this.originalState = execution.getState().getCurrent();
    }

    public ExecutorContext(Execution execution, FlowWithSource flow) {
        this.execution = execution;
        this.flow = flow;
        this.originalState = execution.getState().getCurrent();
    }

    public Boolean canBeProcessed() {
        return !(this.getException() != null || this.getFlow() == null || this.getFlow() instanceof FlowWithException || this.getFlow().getTasks() == null ||
            this.getExecution().isDeleted() || this.getExecution().getState().isPaused() || this.getExecution().getState().isBreakpoint() || this.getExecution().getState().isQueued());
    }

    public ExecutorContext withFlow(FlowWithSource flow) {
        this.flow = flow;

        return this;
    }

    public ExecutorContext withExecution(Execution execution, String from) {
        this.execution = execution;
        this.from.add(from);
        this.executionUpdated = true;

        return this;
    }

    public ExecutorContext withException(Exception exception, String from) {
        this.exception = exception;
        this.from.add(from);

        return this;
    }

    public ExecutorContext withTaskRun(List<TaskRun> taskRuns, String from) {
        this.nexts.addAll(taskRuns);
        this.from.add(from);

        return this;
    }

    public ExecutorContext withWorkerTasks(List<ExecutorWorkerTask> workerTasks, String from) {
        this.workerTasks.addAll(workerTasks);
        this.from.add(from);

        return this;
    }

    public ExecutorContext withWorkerTaskDelays(List<ExecutionDelay> executionDelays, String from) {
        this.executionDelays.addAll(executionDelays);
        this.from.add(from);

        return this;
    }

    public ExecutorContext withSubflowExecutions(List<SubflowExecution<?>> subflowExecutions, String from) {
        this.subflowExecutions.addAll(subflowExecutions);
        this.from.add(from);

        return this;
    }

    public ExecutorContext withSubflowExecutionResults(List<SubflowExecutionResult> subflowExecutionResults, String from) {
        this.subflowExecutionResults.addAll(subflowExecutionResults);
        this.from.add(from);

        return this;
    }
}
