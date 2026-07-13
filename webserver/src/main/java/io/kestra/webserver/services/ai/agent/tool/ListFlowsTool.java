package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.stream.Collectors;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;
import io.kestra.webserver.utils.PageableUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool listing flows matching per-field query filters, one compact line per flow.
 */
@Singleton
public class ListFlowsTool implements AiPlatformTool {
    private static final int MAX_RESULTS = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 120;

    private final FlowRepositoryInterface flowRepository;

    @Inject
    public ListFlowsTool(final FlowRepositoryInterface flowRepository) {
        this.flowRepository = flowRepository;
    }

    @Override
    public AgentToolFamily family() {
        return AgentToolFamily.READ;
    }

    @Override
    public AgentWritePolicy writePolicy() {
        return AgentWritePolicy.AUTO;
    }

    @Tool(
        name = "list-flows",
        value = "List Kestra flows matching the given per-field filters (at most 50), one line per flow with namespace, id and description. Read-only; use this to discover flows before reading one with read-flow."
    )
    public String listFlows(
        @QueryFilterFormat(QueryFilter.Resource.FLOW) List<QueryFilter> filters,
        @TenantId @P(name = "tenantId", value = "The tenant to run against; omit to use your current tenant", required = false) String tenantId) {
        String tenant = AgentCallContext.resolveTenant(tenantId);

        List<Flow> flows = flowRepository.find(PageableUtils.from(1, MAX_RESULTS), tenant, filters);
        if (flows.isEmpty()) {
            return "No flows found matching the given filters.";
        }

        return flows.stream()
            .map(ListFlowsTool::formatLine)
            .collect(Collectors.joining("\n"));
    }

    private static String formatLine(final Flow flow) {
        StringBuilder line = new StringBuilder()
            .append(flow.getNamespace()).append('.').append(flow.getId());
        if (flow.getDescription() != null && !flow.getDescription().isBlank()) {
            line.append(" — ").append(truncate(flow.getDescription().replace('\n', ' ')));
        }
        return line.toString();
    }

    private static String truncate(final String text) {
        return text.length() <= MAX_DESCRIPTION_LENGTH ? text : text.substring(0, MAX_DESCRIPTION_LENGTH) + "…";
    }
}
