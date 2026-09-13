package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.utils.PageableUtils;

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
        value = "List Kestra flows matching the given per-field filters (at most 50). Read-only; use this to discover flows before reading one with read-flow. "
            + "Returns an object { flows } where `flows` is an array of { namespace, id, description } (empty when nothing matches); long descriptions are truncated."
    )
    public Result listFlows(
        @QueryFilterFormat(QueryFilter.Resource.FLOW) List<QueryFilter> filters,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();

        List<Flow> flows = flowRepository.find(PageableUtils.from(1, MAX_RESULTS), tenant, filters);

        return new Result(
            flows.stream()
                .map(ListFlowsTool::toSummary)
                .toList()
        );
    }

    private static FlowSummary toSummary(final Flow flow) {
        String description = flow.getDescription() == null || flow.getDescription().isBlank()
            ? null
            : truncate(flow.getDescription());
        return new FlowSummary(flow.getNamespace(), flow.getId(), description);
    }

    private static String truncate(final String text) {
        return text.length() <= MAX_DESCRIPTION_LENGTH ? text : text.substring(0, MAX_DESCRIPTION_LENGTH) + "…";
    }

    public record Result(List<FlowSummary> flows) {
    }

    public record FlowSummary(String namespace, String id, String description) {
    }
}
