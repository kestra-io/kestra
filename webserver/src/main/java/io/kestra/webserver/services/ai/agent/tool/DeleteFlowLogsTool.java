package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DeleteFlowLogsTool implements McpPlatformTool {
    private final LogDataStoreInterface logRepository;

    @Inject
    public DeleteFlowLogsTool(final LogDataStoreInterface logRepository) {
        this.logRepository = logRepository;
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
        name = "delete_flow_logs",
        value = "Permanently delete the logs of every execution and trigger of one Kestra flow; the flow and its executions are kept. `triggerId` restricts the deletion to the logs of one trigger. "
            + "Deleted logs cannot be recovered, so only call this when the user explicitly asked for it. Returns an object { namespace, flowId } once the logs are deleted."
    )
    public Result deleteFlowLogs(
        @P(name = "namespace", value = "The namespace of the flow") String namespace,
        @P(name = "flowId", value = "The id of the flow whose logs to delete") String flowId,
        @P(name = "triggerId", value = "Only delete the logs of this trigger id", required = false) String triggerId,
        final AgentCallContext.Context context) {
        if (!logRepository.canPurge()) {
            throw new IllegalStateException("The configured log store does not support deleting logs.");
        }

        logRepository.deleteByQuery(context.tenant(), namespace, flowId, triggerId);

        return new Result(namespace, flowId);
    }

    public record Result(String namespace, String flowId) {
    }
}
