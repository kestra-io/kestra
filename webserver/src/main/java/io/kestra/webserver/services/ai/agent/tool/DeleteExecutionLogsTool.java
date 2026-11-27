package io.kestra.webserver.services.ai.agent.tool;

import org.slf4j.event.Level;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DeleteExecutionLogsTool implements McpPlatformTool {
    private final LogDataStoreInterface logRepository;
    private final ExecutionRepositoryInterface executionRepository;

    @Inject
    public DeleteExecutionLogsTool(final LogDataStoreInterface logRepository, final ExecutionRepositoryInterface executionRepository) {
        this.logRepository = logRepository;
        this.executionRepository = executionRepository;
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
        return McpToolAnnotations.IDEMPOTENT_DESTRUCTIVE;
    }

    @Tool(
        name = "delete_execution_logs",
        value = "Permanently delete the logs of one Kestra execution; the execution itself is kept. The optional arguments restrict the deletion to one task, task run, attempt or level and above; omit them all to delete every log of the execution. "
            + "Deleted logs cannot be recovered, so only call this when the user explicitly asked for it. Returns an object { executionId } once the logs are deleted."
    )
    public Result deleteExecutionLogs(
        @P(name = "executionId", value = "The id of the execution whose logs to delete") String executionId,
        @P(name = "taskId", value = "Only delete the logs of this task id", required = false) String taskId,
        @P(name = "taskRunId", value = "Only delete the logs of this task run id", required = false) String taskRunId,
        @P(name = "minLevel", value = "Only delete logs at this level or above", required = false) Level minLevel,
        @P(name = "attempt", value = "Only delete the logs of this attempt number", required = false) Integer attempt,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();
        if (!logRepository.canPurge()) {
            throw new IllegalStateException("The configured log store does not support deleting logs.");
        }
        executionRepository.findById(tenant, executionId)
            .orElseThrow(() -> new IllegalArgumentException("Execution not found: '%s'".formatted(executionId)));

        logRepository.deleteByQuery(tenant, executionId, taskId, taskRunId, minLevel, attempt);

        return new Result(executionId);
    }

    public record Result(String executionId) {
    }
}
