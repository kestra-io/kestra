package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.stream.Collectors;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
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
        value = "List the most recent Kestra executions matching the given per-field filters (newest first, at most 50). Read-only; use this to find executions of interest before inspecting one with read-execution."
    )
    public String listExecutions(
        @QueryFilterFormat(QueryFilter.Resource.EXECUTION) List<QueryFilter> filters,
        @TenantId @P(name = "tenantId", value = "The tenant to run against; omit to use your current tenant", required = false) String tenantId) {
        String tenant = AgentCallContext.resolveTenant(tenantId);

        List<Execution> executions = executionRepository.find(
            PageableUtils.from(1, MAX_RESULTS, List.of(Execution.STATE_START_DATE_FIELD + ":desc"), executionRepository.sortMapping()),
            tenant,
            filters
        );
        if (executions.isEmpty()) {
            return "No executions found matching the given filters.";
        }

        return executions.stream()
            .map(ListExecutionsTool::formatLine)
            .collect(Collectors.joining("\n"));
    }

    private static String formatLine(final Execution execution) {
        StringBuilder line = new StringBuilder()
            .append(execution.getId())
            .append(' ').append(execution.getNamespace()).append('.').append(execution.getFlowId())
            .append(" [").append(execution.getState().getCurrent()).append(']')
            .append(" startDate=").append(execution.getState().getStartDate());
        execution.getState().getDuration().ifPresent(duration -> line.append(" duration=").append(duration));
        return line.toString();
    }
}
