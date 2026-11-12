package io.kestra.webserver.services.ai.agent.tool;

import java.time.Duration;
import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.services.SearchableDataFilterService;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.utils.PageableUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool listing the most recent executions matching per-field query filters,
 * one compact line per execution.
 */
@Singleton
public class ListExecutionsTool implements McpPlatformTool {
    private static final int MAX_RESULTS = 50;

    private final ExecutionRepositoryInterface executionRepository;
    private final SearchableDataFilterService searchableDataFilterService;

    @Inject
    public ListExecutionsTool(final ExecutionRepositoryInterface executionRepository, final SearchableDataFilterService searchableDataFilterService) {
        this.executionRepository = executionRepository;
        this.searchableDataFilterService = searchableDataFilterService;
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
        name = "list_executions",
        value = "List the most recent Kestra executions matching the given per-field filters (newest first, " + MAX_RESULTS + " per page). Read-only; use this to find executions of interest before inspecting one with read_execution. "
            + "Returns an object { executions, total, page } where `executions` is an array of { id, namespace, flowId, state, startDate, duration } (empty when nothing matches); `duration` is an ISO-8601 duration or null. "
            + "`total` is the number of all matching executions; when it exceeds what was read so far, request the next `page`."
    )
    public Result listExecutions(
        @QueryFilterFormat(QueryFilter.Resource.EXECUTION) List<QueryFilter> filters,
        @P(name = "page", value = "The 1-based page to read; omit for the first page", required = false) Integer page,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();
        int pageNumber = ToolPages.pageNumber(page);

        var executions = executionRepository.find(
            PageableUtils.from(pageNumber, MAX_RESULTS, List.of(Execution.STATE_START_DATE_FIELD + ":desc"), executionRepository.sortMapping()),
            tenant,
            searchableDataFilterService.resolve(tenant, filters)
        );

        return new Result(
            executions.stream()
                .map(ListExecutionsTool::toSummary)
                .toList(),
            executions.getTotal(),
            pageNumber
        );
    }

    private static ExecutionSummary toSummary(final Execution execution) {
        String duration = execution.getState().getDuration().map(Duration::toString).orElse(null);
        return new ExecutionSummary(
            execution.getId(),
            execution.getNamespace(),
            execution.getFlowId(),
            execution.getState().getCurrent().name(),
            String.valueOf(execution.getState().getStartDate()),
            duration
        );
    }

    public record Result(List<ExecutionSummary> executions, long total, int page) {
    }

    public record ExecutionSummary(String id, String namespace, String flowId, String state, String startDate, String duration) {
    }
}
