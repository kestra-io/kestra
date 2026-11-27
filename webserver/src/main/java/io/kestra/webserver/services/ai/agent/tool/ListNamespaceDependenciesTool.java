package io.kestra.webserver.services.ai.agent.tool;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.namespaces.NamespaceInterface;
import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowTopologyGraph;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.tool.ListFlowDependenciesTool.Dependency;
import io.kestra.webserver.services.ai.agent.tool.ListFlowDependenciesTool.FlowRef;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool listing the flows depending on the flows of a namespace and its descendants.
 */
@Singleton
public class ListNamespaceDependenciesTool implements McpPlatformTool {
    static final int PAGE_SIZE = 100;

    private static final Comparator<String> NULLS_LAST = Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<FlowRef> FLOW_REF_ORDER = Comparator.comparing(FlowRef::namespace, NULLS_LAST).thenComparing(FlowRef::id, NULLS_LAST);
    private static final Comparator<Dependency> DEPENDENCY_ORDER = Comparator.comparing(Dependency::upstream, FLOW_REF_ORDER)
        .thenComparing(Dependency::downstream, FLOW_REF_ORDER)
        .thenComparing(dependency -> dependency.relation().name());

    private final FlowRepositoryInterface flowRepository;
    private final FlowTopologyService flowTopologyService;

    @Inject
    public ListNamespaceDependenciesTool(final FlowRepositoryInterface flowRepository, final FlowTopologyService flowTopologyService) {
        this.flowRepository = flowRepository;
        this.flowTopologyService = flowTopologyService;
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
        name = "list_namespace_dependencies",
        value = "List the flows that depend on the flows of a Kestra namespace, including its child namespaces, " + PAGE_SIZE + " per page. Read-only; use this to map how the flows of a team or project chain together, "
            + "or what a change in that namespace could break. Every dependency has its `upstream` flow in the namespace; the `downstream` flow can be anywhere. Flows the namespace depends on in other namespaces "
            + "are not listed, and neither are flows of the namespace without any dependency: use list_flow_dependencies on a single flow for those. "
            + "Returns an object { dependencies, total, page } where `dependencies` is an array of { upstream, downstream, relation }; `upstream` and `downstream` are { namespace, id }, both null for a flow the caller cannot see. "
            + "`relation` is FLOW_TASK (downstream runs as a subflow task of upstream), FLOW_TRIGGER (downstream has a Flow trigger on upstream's executions) or SUBFLOW_FUNCTION (downstream reads upstream through an input). "
            + "`total` is the number of all dependencies; when it exceeds what was read so far, request the next `page`."
    )
    public Result listNamespaceDependencies(
        @P(name = "namespace", value = "The namespace whose flows to map, with its child namespaces") String namespace,
        @P(name = "page", value = "The 1-based page of dependencies to read; omit for the first page", required = false) Integer page,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();
        int pageNumber = ToolPages.pageNumber(page);
        if (flowRepository.findDistinctNamespace(tenant).stream().noneMatch(candidate -> NamespaceInterface.isDescendantOrSelf(namespace, candidate))) {
            throw new IllegalArgumentException("No flows found in namespace '%s' or its child namespaces".formatted(namespace));
        }

        FlowTopologyGraph graph = flowTopologyService.namespaceGraph(tenant, namespace);
        Map<String, FlowNode> nodes = graph.getNodes().stream().collect(Collectors.toMap(FlowNode::getUid, Function.identity(), (first, second) -> first));
        List<Dependency> dependencies = graph.getEdges().stream()
            .map(edge -> new Dependency(toFlowRef(nodes.get(edge.getSource()), context), toFlowRef(nodes.get(edge.getTarget()), context), edge.getRelation()))
            .sorted(DEPENDENCY_ORDER)
            .toList();

        return new Result(ToolPages.slice(dependencies, pageNumber, PAGE_SIZE), dependencies.size(), pageNumber);
    }

    /**
     * @return the node to show the caller, which a replacement may anonymize when the caller cannot see that flow
     */
    protected FlowNode visibleNode(final FlowNode node, final AgentCallContext.Context context) {
        return node;
    }

    private FlowRef toFlowRef(final FlowNode node, final AgentCallContext.Context context) {
        if (node == null) {
            return new FlowRef(null, null);
        }
        FlowNode visible = visibleNode(node, context);
        return new FlowRef(visible.getNamespace(), visible.getId());
    }

    public record Result(List<Dependency> dependencies, long total, int page) {
    }
}
