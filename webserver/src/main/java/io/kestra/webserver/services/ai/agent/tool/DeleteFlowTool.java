package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DeleteFlowTool implements McpPlatformTool {
    private final FlowRepositoryInterface flowRepository;
    private final FlowService flowService;

    @Inject
    public DeleteFlowTool(final FlowRepositoryInterface flowRepository, final FlowService flowService) {
        this.flowRepository = flowRepository;
        this.flowService = flowService;
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
        return McpToolAnnotations.IDEMPOTENT_DELETE;
    }

    @Tool(
        name = "delete_flow",
        value = "Delete a Kestra flow: its triggers stop, it can no longer be executed, and it disappears from list_flows. Past executions and their logs are kept. "
            + "Fails when the flow does not exist. Consider disable_flow instead when the flow may be needed again. "
            + "Returns an object { namespace, id, revision } where `revision` is the last revision of the deleted flow."
    )
    public Result deleteFlow(
        @P(name = "namespace", value = "The namespace of the flow") String namespace,
        @P(name = "flowId", value = "The id of the flow") String flowId,
        final AgentCallContext.Context context) {
        FlowWithSource flow = flowRepository.findByIdWithSource(context.tenant(), namespace, flowId)
            .orElseThrow(() -> new IllegalArgumentException("Flow not found: '%s.%s'".formatted(namespace, flowId)));

        flowService.delete(flow);

        return new Result(namespace, flowId, flow.getRevision());
    }

    public record Result(String namespace, String id, Integer revision) {
    }
}
