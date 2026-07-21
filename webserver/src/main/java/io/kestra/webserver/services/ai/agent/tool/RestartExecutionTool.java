package io.kestra.webserver.services.ai.agent.tool;

import java.util.concurrent.TimeoutException;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.async.AsyncOperationsConfiguration;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.executor.command.Restart;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.services.AsyncOperationWaiter;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class RestartExecutionTool implements AiPlatformTool {
    private final ExecutionRepositoryInterface executionRepository;
    private final DispatchQueueInterface<ExecutionCommand> executionCommandQueue;
    private final AsyncOperationWaiter asyncOperationWaiter;
    private final AsyncOperationsConfiguration asyncOperationsConfiguration;

    @Inject
    public RestartExecutionTool(
        final ExecutionRepositoryInterface executionRepository,
        final DispatchQueueInterface<ExecutionCommand> executionCommandQueue,
        final AsyncOperationWaiter asyncOperationWaiter,
        final AsyncOperationsConfiguration asyncOperationsConfiguration) {
        this.executionRepository = executionRepository;
        this.executionCommandQueue = executionCommandQueue;
        this.asyncOperationWaiter = asyncOperationWaiter;
        this.asyncOperationsConfiguration = asyncOperationsConfiguration;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.ACT;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.CONFIRM;
    }

    @Tool(
        name = "restart-execution", value = "Restart a failed or paused Kestra execution from its failed tasks. The execution must be in a terminated or paused state. "
            + "Blocks until the executor accepts the restart, then returns an object { executionId }. A successful return means the restart was accepted and the execution is re-running; "
            + "its state then changes asynchronously, so do NOT assume the final state from this call — tell the user to watch the execution page for progress."
    )
    public Result restartExecution(
        @P(name = "executionId", value = "The id of the execution to restart") String executionId,
        @P(name = "revision", value = "Optional flow revision to restart with; omit to use the execution's own revision", required = false) Integer revision,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();

        Execution execution = executionRepository.findById(tenant, executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: '%s'".formatted(executionId)));

        if (!execution.getState().canBeRestarted()) {
            throw new IllegalStateException(
                "Execution '%s' cannot be restarted: current state is '%s', expected terminated or paused."
                    .formatted(executionId, execution.getState().getCurrent())
            );
        }

        AsyncOperationProcessedEvent processed;
        try {
            processed = asyncOperationWaiter.submitAndWait(
                executionId,
                operationId ->
                {
                    try {
                        executionCommandQueue.emit(Restart.from(execution, revision).withOperationId(operationId));
                    } catch (QueueException e) {
                        throw new RuntimeException(e);
                    }
                },
                asyncOperationsConfiguration.waitTimeout()
            );
        } catch (TimeoutException e) {
            throw new IllegalStateException(
                "Timed out waiting for execution '%s' to be restarted.".formatted(executionId), e
            );
        }

        if (processed.outcome() == AsyncOperationProcessedEvent.Outcome.FAILED) {
            throw new IllegalStateException(
                "Failed to restart execution '%s': %s".formatted(executionId, processed.error())
            );
        }

        return new Result(
            executionId
        );
    }

    /**
     * Acknowledgement that a restart was accepted by the executor.
     *
     * @param executionId the execution that was restarted
     */
    public record Result(String executionId) {
    }
}
