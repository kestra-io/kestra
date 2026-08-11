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
import io.kestra.core.runners.WorkerJobEvent;
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
    private FlowWithSource flow;

    private final List<String> from = new ArrayList<>(8);
    private boolean executionUpdated = false;
    private int nextCount = 0;

    // We usually only use one of the lists above, not many in each processing loop.
    // And as we always use addAll the capacity of the list will grow to what's needed when used,
    // so we initialize them with 0 to save memory.
    private final List<ExecutorWorkerTask> workerTasks = new ArrayList<>(0);
    private final List<WorkerJobEvent> workerJobEvents = new ArrayList<>(0);
    private final List<ExecutionDelay> executionDelays = new ArrayList<>(0);
    private final List<SubflowExecution<?>> subflowExecutions = new ArrayList<>(0);
    private final List<SubflowExecutionResult> subflowExecutionResults = new ArrayList<>(0);
    private final List<Execution> loopExecutions = new ArrayList<>(0);
    /**
     * The execution as loaded at cycle entry when it was <b>already in a terminal
     * state</b> then, and {@code null} otherwise.
     */
    private final Execution terminalExecutionAtEntry;
    // Tracks every distinct state this execution passes through within a single cycle.
    // Index 0 = state at cycle entry; each subsequent entry is a new state.
    private final List<State.Type> stateTransitions = new ArrayList<>(1);

    public ExecutorContext(Execution execution) {
        this.execution = execution;
        this.terminalExecutionAtEntry = execution.getState().isTerminated() ? execution : null;
        this.stateTransitions.add(execution.getState().getCurrent());
    }

    public ExecutorContext(Execution execution, FlowWithSource flow) {
        this.execution = execution;
        this.flow = flow;
        this.terminalExecutionAtEntry = execution.getState().isTerminated() ? execution : null;
        this.stateTransitions.add(execution.getState().getCurrent());
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
        State.Type newState = execution.getState().getCurrent();
        if (!newState.equals(stateTransitions.getLast())) {
            stateTransitions.add(newState);
        }

        return this;
    }

    public ExecutorContext withException(Exception exception, String from) {
        this.exception = exception;
        this.from.add(from);

        return this;
    }

    /**
     * Callers must only pass task runs not already present in the execution.
     */
    public ExecutorContext withTaskRun(List<TaskRun> taskRuns, String from) {
        this.from.add(from);

        // Merge into the execution immediately, so a task run created earlier in this same process() pass is visible to later steps of the same pass
        // (e.g. handleWorkerTasks dispatching a child created by handleFlowableTasks in the same cycle).
        if (!taskRuns.isEmpty()) {
            List<TaskRun> merged = this.execution.getTaskRunList() == null ? new ArrayList<>() : new ArrayList<>(this.execution.getTaskRunList());
            merged.addAll(taskRuns);
            this.execution = this.execution.withTaskRunList(merged);
            this.executionUpdated = true;
            this.nextCount = nextCount + taskRuns.size();
        }

        return this;
    }

    public ExecutorContext withWorkerTasks(List<ExecutorWorkerTask> workerTasks, String from) {
        this.workerTasks.addAll(workerTasks);
        this.from.add(from);

        return this;
    }

    public ExecutorContext withWorkerJobEvent(WorkerJobEvent event) {
        this.workerJobEvents.add(event);

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

    public ExecutorContext withLoopExecution(Execution loopExecution, String from) {
        this.loopExecutions.add(loopExecution);
        this.from.add(from);

        return this;
    }
}
