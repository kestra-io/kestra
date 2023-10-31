package io.kestra.core.models.tasks;

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

/**
 * Interface for tasks that generates subflow execution(s). Those tasks are handled in the Executor.
 */
public interface ExecutableTask {
    /**
     * Creates a list of WorkerTaskExecution for this task definition.
     * Each WorkerTaskExecution will generate a subflow execution.
     */
    List<WorkerTaskExecution<?>> createWorkerTaskExecutions(RunContext runContext,
                                     FlowExecutorInterface flowExecutorInterface,
                                     Flow currentFlow, Execution currentExecution,
                                     TaskRun currentTaskRun) throws InternalException;

    /**
     * Creates a WorkerTaskResult for a given WorkerTaskExecution
     */
    Optional<WorkerTaskResult> createWorkerTaskResult(RunContext runContext,
                                                      WorkerTaskExecution<?> workerTaskExecution,
                                                      Flow flow,
                                                      Execution execution);

    /**
     * Whether to wait for the execution(s) of the subflow before terminating this tasks
     */
    boolean waitForExecution();

    /**
     * @return the subflow identifier, used by the flow topology and related dependency code.
     */
    SubflowId subflowId();

    record SubflowId(String namespace, String flowId, Optional<Integer> revision) {
        public String flowUid() {
            // as the Flow task can only be used in the same tenant we can hardcode null here
            return Flow.uid(null, this.namespace, this.flowId, this.revision);
        }

        public String flowUidWithoutRevision() {
            // as the Flow task can only be used in the same tenant we can hardcode null here
            return Flow.uidWithoutRevision(null, this.namespace, this.flowId);
        }
    }
}
