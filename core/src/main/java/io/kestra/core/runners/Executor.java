package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.executions.*;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.State;
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
    private ExecutionResumed executionResumed;
    private ExecutionResumed joinedExecutionResumed;
    private final List<WorkerTrigger> workerTriggers = new ArrayList<>();
    private WorkerJob workerJobToResubmit;
    private State.Type originalState;
    private SubflowExecutionEnd subflowExecutionEnd;
    private SubflowExecutionEnd joinedSubflowExecutionEnd;

    /**
     * The sequence id should be incremented each time the execution is persisted after mutation.
     */
    private long seqId = 0L;

    /**
     * List of {@link ExecutionKilled} to be propagated part of the execution.
     */
    private List<ExecutionKilledExecution> executionKilled;

    public Executor(Execution execution, Long offset) {
        this.execution = execution;
        this.offset = offset;
        this.originalState = execution.getState().getCurrent();
    }

    public Executor(Execution execution, Long offset, long seqId) {
        this.execution = execution;
        this.offset = offset;
        this.seqId = seqId;
        this.originalState = execution.getState().getCurrent();
    }

    public Executor(WorkerTaskResult workerTaskResult) {
        this.joinedWorkerTaskResult = workerTaskResult;
    }

    public Executor(SubflowExecutionResult subflowExecutionResult) {
        this.joinedSubflowExecutionResult = subflowExecutionResult;
    }

    public Executor(SubflowExecutionEnd subflowExecutionEnd) {
        this.joinedSubflowExecutionEnd = subflowExecutionEnd;
    }

    public Executor(WorkerJob workerJob) {
        this.workerJobToResubmit = workerJob;
    }

    public Executor(ExecutionResumed executionResumed) {
        this.joinedExecutionResumed = executionResumed;
    }

    public Executor(List<ExecutionKilledExecution> executionKilled) {
        this.executionKilled = executionKilled;
    }

    public Boolean canBeProcessed() {
        return !(this.getException() != null || this.getFlow() == null || this.getFlow() instanceof FlowWithException || this.getFlow().getTasks() == null || this.getExecution().isDeleted() || this.getExecution().getState().isPaused());
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

    public Executor withWorkerTriggers(List<WorkerTrigger> workerTriggers, String from) {
        this.workerTriggers.addAll(workerTriggers);
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

    public Executor withExecutionRunning(ExecutionRunning executionRunning) {
        this.executionRunning = executionRunning;

        return this;
    }

    public Executor withExecutionResumed(ExecutionResumed executionResumed) {
        this.executionResumed = executionResumed;
        return this;
    }

    public Executor withExecutionKilled(final List<ExecutionKilledExecution> executionKilled) {
        this.executionKilled = executionKilled;
        return this;
    }

    public Executor withSubflowExecutionEnd(SubflowExecutionEnd subflowExecutionEnd) {
        this.subflowExecutionEnd = subflowExecutionEnd;
        return this;
    }

    public Executor serialize() {
        return new Executor(
            this.execution,
            this.offset,
            this.seqId
        );
    }

    /**
     * Increments and returns the execution sequence id.
     *
     * @return the sequence id.
     */
    public long incrementAndGetSeqId() {
        this.seqId++;
        return seqId;
    }
}
