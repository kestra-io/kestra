package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool listing the flows a flow depends on and the flows depending on it.
 */
@Singleton
public class ListFlowDependenciesTool implements McpPlatformTool {
    private static final int MAX_RESULTS = 100;

    private final FlowRepositoryInterface flowRepository;
    private final FlowService flowService;

    @Inject
    public ListFlowDependenciesTool(final FlowRepositoryInterface flowRepository, final FlowService flowService) {
        this.flowRepository = flowRepository;
        this.flowService = flowService;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.READ;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.AUTO;
    }

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.READ_ONLY;
    }

    @Tool(
        name = "list_flow_dependencies",
        value = "List the dependencies between a Kestra flow and other flows (" + MAX_RESULTS + " per page). Read-only; use this to see what a change to a flow could break, or what runs before or after it. "
            + "Returns an object { dependencies, total, page } where `dependencies` is an array of { upstream, downstream, relation }; `upstream` and `downstream` are { namespace, id }, "
            + "both null for a flow the caller cannot see. `relation` is FLOW_TASK (downstream runs as a subflow task of upstream), FLOW_TRIGGER (downstream has a Flow trigger on upstream's executions) "
            + "or SUBFLOW_FUNCTION (downstream reads upstream through an input). `total` is the number of all dependencies; when it exceeds what was read so far, request the next `page`."
    )
    public Result listFlowDependencies(
        @P(name = "namespace", value = "The namespace of the flow") String namespace,
        @P(name = "flowId", value = "The id of the flow") String flowId,
        @P(name = "downstreamOnly", value = "Only list the flows that depend on this flow; default false", required = false) Boolean downstreamOnly,
        @P(name = "transitive", value = "Also follow the dependencies of the dependencies, recursively; default false", required = false) Boolean transitive,
        @P(name = "page", value = "The 1-based page to read; omit for the first page", required = false) Integer page,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();
        int pageNumber = ToolPages.pageNumber(page);
        if (flowRepository.findById(tenant, namespace, flowId).isEmpty()) {
            throw new IllegalArgumentException("Flow not found: '%s.%s'".formatted(namespace, flowId));
        }

        List<Dependency> dependencies = flowService
            .findDependencies(tenant, namespace, flowId, Boolean.TRUE.equals(downstreamOnly), Boolean.TRUE.equals(transitive))
            .map(topology -> toDependency(topology, context))
            .distinct()
            .toList();

        return new Result(ToolPages.slice(dependencies, pageNumber, MAX_RESULTS), dependencies.size(), pageNumber);
    }

    /**
     * @return the node to show the caller, which a replacement may anonymize when the caller cannot see that flow
     */
    protected FlowNode visibleNode(final FlowNode node, final AgentCallContext.Context context) {
        return node;
    }

    private Dependency toDependency(final FlowTopology topology, final AgentCallContext.Context context) {
        return new Dependency(
            toFlowRef(visibleNode(topology.getSource(), context)),
            toFlowRef(visibleNode(topology.getDestination(), context)),
            topology.getRelation()
        );
    }

    private static FlowRef toFlowRef(final FlowNode node) {
        return new FlowRef(node.getNamespace(), node.getId());
    }

    public record Result(List<Dependency> dependencies, long total, int page) {
    }

    public record Dependency(FlowRef upstream, FlowRef downstream, FlowRelation relation) {
    }

    public record FlowRef(String namespace, String id) {
    }
}
