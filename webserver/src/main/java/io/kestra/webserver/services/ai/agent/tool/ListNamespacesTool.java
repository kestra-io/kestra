package io.kestra.webserver.services.ai.agent.tool;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.contexts.configuration.SystemFlowsConfiguration;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.namespaces.Namespace;
import io.kestra.core.models.namespaces.NamespaceInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.utils.Searchable;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool listing the namespaces of a tenant, derived from the flows like the namespaces REST endpoint.
 */
@Singleton
public class ListNamespacesTool implements McpPlatformTool {
    static final int PAGE_SIZE = 100;

    private final FlowRepositoryInterface flowRepository;
    private final SystemFlowsConfiguration systemFlowsConfiguration;
    private final Searchable<Namespace> namespaceSearchable;

    @Inject
    public ListNamespacesTool(
        final FlowRepositoryInterface flowRepository,
        final SystemFlowsConfiguration systemFlowsConfiguration,
        @Named("NAMESPACE") final Searchable<Namespace> namespaceSearchable) {
        this.flowRepository = flowRepository;
        this.systemFlowsConfiguration = systemFlowsConfiguration;
        this.namespaceSearchable = namespaceSearchable;
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
        name = "list_namespaces",
        value = "List the namespaces of the Kestra tenant matching the given per-field filters, sorted by id, " + PAGE_SIZE + " per page. Read-only; use this to discover namespaces before listing the flows, "
            + "files or KV pairs of one. Every namespace holding a flow is listed along with all of its parents (a flow in `company.team.etl` lists `company`, `company.team` and `company.team.etl`), plus the system namespace. "
            + "Returns an object { namespaces, total, page } where `namespaces` is an array of { id, hasFlows } (empty when nothing matches); `hasFlows` is true when at least one flow is defined directly in the namespace, "
            + "false for a namespace that only groups child namespaces. `total` is the number of all matching namespaces; when it exceeds what was read so far, request the next `page`."
    )
    public Result listNamespaces(
        @QueryFilterFormat(QueryFilter.Resource.NAMESPACE) List<QueryFilter> filters,
        @P(name = "withFlowsOnly", value = "Only list the namespaces in which at least one flow is defined, leaving out the parents that merely group them; default false", required = false) Boolean withFlowsOnly,
        @P(name = "page", value = "The 1-based page of namespaces to read; omit for the first page", required = false) Integer page,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();
        int pageNumber = ToolPages.pageNumber(page);

        Set<String> withFlows = new HashSet<>(flowRepository.findDistinctNamespace(tenant));
        Stream<String> ids = Boolean.TRUE.equals(withFlowsOnly)
            ? withFlows.stream()
            : Stream.concat(Stream.concat(withFlows.stream(), additionalNamespaces(tenant)), Stream.of(systemFlowsConfiguration.namespace()))
                .flatMap(namespace -> NamespaceInterface.asTree(namespace).stream());
        List<Namespace> namespaces = ids.distinct().map(id -> (Namespace) Namespace.builder().id(id).build()).toList();

        ArrayListTotal<Namespace> matching = namespaceSearchable.filter(namespaces, pageNumber, PAGE_SIZE, List.of("id:asc"), filters);

        return new Result(
            matching.stream().map(namespace -> new NamespaceSummary(namespace.getId(), withFlows.contains(namespace.getId()))).toList(),
            matching.getTotal(),
            pageNumber
        );
    }

    /**
     * @return namespaces to list besides the ones holding flows, for a replacement that stores namespaces of its own
     */
    protected Stream<String> additionalNamespaces(final String tenant) {
        return Stream.empty();
    }

    public record Result(List<NamespaceSummary> namespaces, long total, int page) {
    }

    public record NamespaceSummary(String id, boolean hasFlows) {
    }
}
