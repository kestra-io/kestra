package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.services.FlowService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Agent tool creating a new flow from its YAML source.
 */
@Singleton
public class CreateFlowTool implements McpPlatformTool {
    private final FlowService flowService;

    @Inject
    public CreateFlowTool(final FlowService flowService) {
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
        return McpToolAnnotations.ADDITIVE;
    }

    @Tool(
        name = "create_flow",
        value = "Create a new Kestra flow from its full YAML source; the flow's namespace and id are read from the YAML. Fails when a flow with that namespace and id already exists (use update_flow to change it) "
            + "or when the YAML is invalid (check it first with validate_flow). The flow is live once created: its triggers start running unless it sets `disabled: true`. "
            + "Returns an object { namespace, id, revision } where `revision` is the created revision, 1 for a new flow."
    )
    public Result createFlow(
        @P(name = "flowYaml", value = "The full YAML source of the flow, including its `id` and `namespace`") String flowYaml,
        final AgentCallContext.Context context) {
        GenericFlow flow = GenericFlow.fromYaml(context.tenant(), flowYaml);
        beforeCreate(flow, context);

        FlowWithSource created = FlowWrites.create(flowService, flow);

        return new Result(created.getNamespace(), created.getId(), created.getRevision());
    }

    /**
     * Called with the parsed flow before it is saved, so that a replacement can reject the call.
     */
    protected void beforeCreate(final GenericFlow flow, final AgentCallContext.Context context) {
    }

    public record Result(String namespace, String id, Integer revision) {
    }
}
