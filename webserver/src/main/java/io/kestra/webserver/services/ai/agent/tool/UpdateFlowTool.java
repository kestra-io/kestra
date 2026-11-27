package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Agent tool replacing the source of an existing flow, which saves it as a new revision.
 */
@Singleton
public class UpdateFlowTool implements McpPlatformTool {
    private final FlowRepositoryInterface flowRepository;
    private final FlowService flowService;

    @Inject
    public UpdateFlowTool(final FlowRepositoryInterface flowRepository, final FlowService flowService) {
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
        name = "update_flow",
        value = "Replace the YAML source of an existing Kestra flow, saving it as a new revision; the flow's namespace and id are read from the YAML and cannot be changed. "
            + "Read the current source with read_flow first and send the full updated YAML, not a fragment. Fails when the flow does not exist (use create_flow) or when the YAML is invalid (check it first with validate_flow). "
            + "Earlier revisions are kept and can be read with read_flow. Sending a source identical to the current one changes nothing. "
            + "Returns an object { namespace, id, revision } where `revision` is the flow's revision after the call."
    )
    public Result updateFlow(
        @P(name = "flowYaml", value = "The full updated YAML source of the flow, including its `id` and `namespace`") String flowYaml,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();
        GenericFlow flow = GenericFlow.fromYaml(tenant, flowYaml);
        beforeUpdate(flow, context);
        FlowWithSource previous = flowRepository.findByIdWithSource(tenant, flow.getNamespace(), flow.getId())
            .orElseThrow(() -> new IllegalArgumentException("Flow not found: '%s.%s'. Use create_flow to create it.".formatted(flow.getNamespace(), flow.getId())));

        FlowWithSource updated = FlowWrites.update(flowService, flow, previous);

        return new Result(updated.getNamespace(), updated.getId(), updated.getRevision());
    }

    /**
     * Called with the parsed flow before it is saved, so that a replacement can reject the call.
     */
    protected void beforeUpdate(final GenericFlow flow, final AgentCallContext.Context context) {
    }

    public record Result(String namespace, String id, Integer revision) {
    }
}
