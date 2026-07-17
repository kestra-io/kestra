package io.kestra.webserver.services.ai.agent.tool;

import java.time.Duration;
import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.utils.PageableUtils;

import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Read-only agent tool listing the most recent executions matching per-field query filters,
 * one compact line per execution.
 */
@Singleton
public class ListExecutionsTool implements AiPlatformTool {
    private static final int MAX_RESULTS = 50;

    private final ExecutionRepositoryInterface executionRepository;

    @Inject
    public ListExecutionsTool(final ExecutionRepositoryInterface executionRepository) {
        this.executionRepository = executionRepository;
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
        name = "list-executions",
        value = "List the most recent Kestra executions matching the given per-field filters (newest first, at most 50). Read-only; use this to find executions of interest before inspecting one with read-execution. "
            + "Returns an object { executions } where `executions` is an array of { id, namespace, flowId, state, startDate, duration } (empty when nothing matches); `duration` is an ISO-8601 duration or null."
    )
    public Result listExecutions(
        @QueryFilterFormat(QueryFilter.Resource.EXECUTION) List<QueryFilter> filters,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();

        List<Execution> executions = executionRepository.find(
            PageableUtils.from(1, MAX_RESULTS, List.of(Execution.STATE_START_DATE_FIELD + ":desc"), executionRepository.sortMapping()),
            tenant,
            filters
        );

        return new Result(
            executions.stream()
                .map(ListExecutionsTool::toSummary)
                .toList()
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

    public record Result(List<ExecutionSummary> executions) {
    }

    public record ExecutionSummary(String id, String namespace, String flowId, String state, String startDate, String duration) {
    }
}
