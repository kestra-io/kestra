package io.kestra.core.models.tasks;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTaskExecution;
import io.kestra.core.runners.WorkerTaskResult;

import java.util.List;
import java.util.Optional;

public interface ExecutableTask<T extends Output>  {
    List<TaskRun> createTaskRun(RunContext runContext, Execution currentExecution, TaskRun executionTaskRun);

    Execution createExecution(RunContext runContext, FlowExecutorInterface flowExecutorInterface, Execution currentExecution) throws InternalException;

    WorkerTaskResult createWorkerTaskResult(RunContext runContext,
                                                     WorkerTaskExecution<?> workerTaskExecution,
                                                     Flow flow,
                                                     Execution execution);

    boolean waitForExecution();
}
