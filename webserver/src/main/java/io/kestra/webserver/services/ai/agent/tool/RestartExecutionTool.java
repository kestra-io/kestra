package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.executor.command.Restart;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class RestartExecutionTool implements AiPlatformTool {
    private final ExecutionRepositoryInterface executionRepository;
    private final DispatchQueueInterface<ExecutionCommand> executionCommandQueue;

    @Inject
    public RestartExecutionTool(
        final ExecutionRepositoryInterface executionRepository,
        final DispatchQueueInterface<ExecutionCommand> executionCommandQueue) {
        this.executionRepository = executionRepository;
        this.executionCommandQueue = executionCommandQueue;
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
        name = "restart-execution", value = "Restart a failed or paused Kestra execution from its failed tasks, creating a new run. The execution must be in a terminated or paused state. "
            + "Returns an object { executionId, operationId } where `operationId` identifies the asynchronously enqueued restart request."
    )
    public Result restartExecution(
        @P(name = "executionId", value = "The id of the execution to restart") String executionId,
        @P(name = "revision", value = "Optional flow revision to restart with; omit to use the execution's own revision", required = false) Integer revision,
        @TenantId @P(name = "tenantId", value = "The tenant to run against; omit to use your current tenant", required = false) String tenantId) {
        String tenant = AgentCallContext.resolveTenant(tenantId);

        Execution execution = executionRepository.findById(tenant, executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: '" + executionId + "'"));

        if (!execution.getState().canBeRestarted()) {
            throw new IllegalStateException(
                "Execution '" + executionId + "' cannot be restarted: current state is '"
                    + execution.getState().getCurrent() + "', expected terminated or paused."
            );
        }

        String operationId = IdUtils.create();
        try {
            executionCommandQueue.emit(Restart.from(execution, revision).withOperationId(operationId));
        } catch (QueueException e) {
            throw new IllegalStateException("Failed to enqueue restart for execution '" + executionId + "': " + e.getMessage(), e);
        }

        return new Result(executionId, operationId);
    }

    /**
     * Acknowledgement of an enqueued restart request.
     *
     * @param executionId the execution that was restarted
     * @param operationId the id identifying the asynchronously enqueued restart request
     */
    public record Result(String executionId, String operationId) {
    }
}
