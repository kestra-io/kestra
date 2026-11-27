package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class DisableFlowTool implements McpPlatformTool {
    private final FlowRepositoryInterface flowRepository;
    private final FlowService flowService;

    @Inject
    public DisableFlowTool(final FlowRepositoryInterface flowRepository, final FlowService flowService) {
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
        return McpToolAnnotations.IDEMPOTENT_UPDATE;
    }

    @Tool(
        name = "disable_flow",
        value = "Disable a Kestra flow so that its triggers stop and it can no longer be executed; executions already running are not stopped. Idempotent: disabling a flow that is already disabled changes nothing. Saves the flow with `disabled: true` as a new revision. "
            + "Returns an object { namespace, id, changed } where `changed` is false when the flow was already disabled."
    )
    public Result disableFlow(
        @P(name = "namespace", value = "The namespace of the flow") String namespace,
        @P(name = "flowId", value = "The id of the flow") String flowId,
        final AgentCallContext.Context context) {
        boolean changed = FlowWrites.setDisabled(flowRepository, flowService, context.tenant(), namespace, flowId, true);

        return new Result(namespace, flowId, changed);
    }

    public record Result(String namespace, String id, boolean changed) {
    }
}
