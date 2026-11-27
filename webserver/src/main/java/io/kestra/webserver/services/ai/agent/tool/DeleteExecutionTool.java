package io.kestra.webserver.services.ai.agent.tool;

import java.io.IOException;
import java.io.UncheckedIOException;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DeleteExecutionTool implements McpPlatformTool {
    private final ExecutionCommandSubmitter submitter;
    private final ExecutionService executionService;
    private final FlowRepositoryInterface flowRepository;

    @Inject
    public DeleteExecutionTool(final ExecutionCommandSubmitter submitter, final ExecutionService executionService, final FlowRepositoryInterface flowRepository) {
        this.submitter = submitter;
        this.executionService = executionService;
        this.flowRepository = flowRepository;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.MUTATE;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.CONFIRM;
    }

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.IDEMPOTENT_DESTRUCTIVE;
    }

    @Tool(
        name = "delete_execution",
        value = "Permanently delete an ended Kestra execution together with its logs, metrics and stored files. This cannot be undone. "
            + "Fails when the execution has not ended yet; kill it with kill_execution first. Returns an object { executionId }."
    )
    public Result deleteExecution(
        @P(name = "executionId", value = "The id of the execution to delete") String executionId,
        final AgentCallContext.Context context) {
        Execution execution = submitter.findExecution(context.tenant(), executionId);

        if (!execution.getState().isTerminated()) {
            throw new IllegalStateException(
                "Execution '%s' cannot be deleted: current state is '%s', expected an ended execution. Kill it first."
                    .formatted(executionId, execution.getState().getCurrent())
            );
        }

        try {
            executionService.cancelOpenApprovals(execution, flowRepository.findByExecutionWithoutAcl(execution));
        } catch (RuntimeException e) {
            log.warn("Unable to close the approval requests of the execution '{}' before deleting it", executionId, e);
        }
        try {
            executionService.delete(execution, true, true, true);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to delete the files of execution '%s'.".formatted(executionId), e);
        }

        return new Result(executionId);
    }

    /**
     * @param executionId the execution that was deleted
     */
    public record Result(String executionId) {
    }
}
