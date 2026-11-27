package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.exceptions.ConflictException;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.executor.command.ForceRun;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ForceRunExecutionTool implements McpPlatformTool {
    private final ExecutionRepositoryInterface executionRepository;
    private final FlowRepositoryInterface flowRepository;
    private final ExecutionService executionService;
    private final ExecutionCommandSubmitter commandSubmitter;

    @Inject
    public ForceRunExecutionTool(
        final ExecutionRepositoryInterface executionRepository,
        final FlowRepositoryInterface flowRepository,
        final ExecutionService executionService,
        final ExecutionCommandSubmitter commandSubmitter) {
        this.executionRepository = executionRepository;
        this.flowRepository = flowRepository;
        this.executionService = executionService;
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
        name = "force_run_execution", value = "Force a Kestra execution that is not terminated (for example CREATED, QUEUED behind a concurrency limit, or PAUSED) to run now, "
            + "bypassing its concurrency limit, schedule or pause. An execution paused on an Approval task cannot be forced: it must be approved or rejected by a reviewer in the UI. "
            + "Blocks until the executor accepts the command, then returns { executionId }. The execution then runs asynchronously, so do NOT assume its final state from this call."
    )
    public Result forceRunExecution(
        @P(name = "executionId", value = "The id of the execution to force run") String executionId,
        final AgentCallContext.Context context) {
        Execution execution = executionRepository.findById(context.tenant(), executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: '%s'".formatted(executionId)));

        if (execution.getState().isTerminated()) {
            throw new IllegalStateException(
                "Execution '%s' cannot be force run: current state is '%s', expected not terminated."
                    .formatted(executionId, execution.getState().getCurrent())
            );
        }
        if (isPausedOnApproval(execution)) {
            throw new ConflictException(ExecutionService.APPROVAL_RESUME_REFUSED.formatted(executionId));
        }

        commandSubmitter.submitAndWait(
            executionId, "force run",
            (queue, operationId, actor) -> queue.emit(ForceRun.from(execution).withOperationId(operationId).withActor(actor))
        );

        return new Result(executionId);
    }

    private boolean isPausedOnApproval(final Execution execution) {
        if (!execution.getState().isPaused()) {
            return false;
        }
        try {
            return executionService.isPausedOnApproval(execution, flowRepository.findByExecutionWithoutAcl(execution));
        } catch (InternalException | FlowProcessingException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * Acknowledgement that a force run was accepted by the executor.
     *
     * @param executionId the execution that was forced to run
     */
    public record Result(String executionId) {
    }
}
