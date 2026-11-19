package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.executor.command.Restart;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class RestartExecutionTool implements McpPlatformTool {
    private final ExecutionRepositoryInterface executionRepository;
    private final ExecutionCommandSubmitter commandSubmitter;

    @Inject
    public RestartExecutionTool(final ExecutionRepositoryInterface executionRepository, final ExecutionCommandSubmitter commandSubmitter) {
        this.executionRepository = executionRepository;
        this.commandSubmitter = commandSubmitter;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.ACT;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.CONFIRM;
    }

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.DESTRUCTIVE;
    }

    @Tool(
        name = "restart_execution", value = "Restart a failed or paused Kestra execution from its failed tasks. The execution must be in a terminated or paused state. "
            + "Blocks until the executor accepts the restart, then returns an object { executionId }. A successful return means the restart was accepted and the execution is re-running; "
            + "its state then changes asynchronously, so do NOT assume the final state from this call — tell the user to watch the execution page for progress."
    )
    public Result restartExecution(
        @P(name = "executionId", value = "The id of the execution to restart") String executionId,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();

        Execution execution = executionRepository.findById(tenant, executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: '%s'".formatted(executionId)));

        if (!execution.getState().canBeRestarted()) {
            throw new IllegalStateException(
                "Execution '%s' cannot be restarted: current state is '%s', expected terminated."
                    .formatted(executionId, execution.getState().getCurrent())
            );
        }

        commandSubmitter.submitAndWait(
            executionId, "restart",
            (queue, operationId, actor) -> queue.emit(Restart.from(execution, null).withOperationId(operationId).withActor(context.actor()))
        );

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
