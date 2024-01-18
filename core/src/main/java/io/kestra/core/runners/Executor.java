package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class Executor {
    private Execution execution;
    private Exception exception;
    private final List<String> from = new ArrayList<>();
    private Long offset;
    @JsonIgnore
    private boolean executionUpdated = false;
    private Flow flow;
    private final List<TaskRun> nexts = new ArrayList<>();
    private final List<WorkerTask> workerTasks = new ArrayList<>();
    private final List<WorkerTaskResult> workerTaskResults = new ArrayList<>();
    private final List<ExecutionDelay> executionDelays = new ArrayList<>();
    private WorkerTaskResult joinedWorkerTaskResult;
    private final List<SubflowExecution<?>> subflowExecutions = new ArrayList<>();
    private final List<SubflowExecutionResult> subflowExecutionResults = new ArrayList<>();
    private SubflowExecutionResult joinedSubflowExecutionResult;
    private ExecutionRunning executionRunning;
    private ExecutionQueued executionQueued;
    private ExecutionResumed executionResumed;
    private ExecutionResumed joinedExecutionResumed;

    public Executor(Execution execution, Long offset) {
        this.execution = execution;
        this.offset = offset;
    }

    public Executor(WorkerTaskResult workerTaskResult) {
        this.joinedWorkerTaskResult = workerTaskResult;
    }

    public Executor(SubflowExecutionResult subflowExecutionResult) {
        this.joinedSubflowExecutionResult = subflowExecutionResult;
    }

    public Executor(ExecutionResumed executionResumed) {
        this.joinedExecutionResumed = executionResumed;
    }

    public Boolean canBeProcessed() {
        return !(this.getException() != null || this.getFlow() == null || this.getFlow() instanceof FlowWithException || this.getFlow().getTasks() == null || this.getExecution().isDeleted());
    }

    public Executor withFlow(Flow flow) {
        this.flow = flow;

        return this;
    }

    public Executor withExecution(Execution execution, String from) {
        this.execution = execution;
        this.from.add(from);
        this.executionUpdated = true;

        return this;
    }

    public Executor withException(Exception exception, String from) {
        this.exception = exception;
        this.from.add(from);
        this.executionUpdated = true;

        return this;
    }

    public Executor withTaskRun(List<TaskRun> taskRuns, String from) {
        this.nexts.addAll(taskRuns);
        this.from.add(from);

        return this;
    }

    public Executor withWorkerTasks(List<WorkerTask> workerTasks, String from) {
        this.workerTasks.addAll(workerTasks);
        this.from.add(from);

        return this;
    }

    public Executor withWorkerTaskResults(List<WorkerTaskResult> workerTaskResults, String from) {
        this.workerTaskResults.addAll(workerTaskResults);
        this.from.add(from);

        return this;
    }

    public Executor withWorkerTaskDelays(List<ExecutionDelay> executionDelays, String from) {
        this.executionDelays.addAll(executionDelays);
        this.from.add(from);

        return this;
    }

    public Executor withSubflowExecutions(List<SubflowExecution<?>> subflowExecutions, String from) {
        this.subflowExecutions.addAll(subflowExecutions);
        this.from.add(from);

        return this;
    }

    public Executor withSubflowExecutionResults(List<SubflowExecutionResult> subflowExecutionResults, String from) {
        this.subflowExecutionResults.addAll(subflowExecutionResults);
        this.from.add(from);

        return this;
    }

    public Executor withExecutionQueued(ExecutionQueued executionQueued) {
        this.executionQueued = executionQueued;

        return this;
    }

    public Executor withExecutionRunning(ExecutionRunning executionRunning) {
        this.executionRunning = executionRunning;

        return this;
    }

    public Executor withExecutionResumed(ExecutionResumed executionResumed) {
        this.executionResumed = executionResumed;

        return this;
    }

    public Executor serialize() {
        return new Executor(
            this.execution,
            this.offset
        );
    }
}
