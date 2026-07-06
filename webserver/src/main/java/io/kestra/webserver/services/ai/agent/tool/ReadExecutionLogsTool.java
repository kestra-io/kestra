package io.kestra.webserver.services.ai.agent.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.ToolFamily;
import io.kestra.webserver.services.ai.agent.domain.WritePolicy;
import io.kestra.webserver.utils.PageableUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ReadExecutionLogsTool implements AiPlatformTool {
    private static final int MAX_LOGS = 500;

    private final LogRepositoryInterface logRepository;

    @Inject
    public ReadExecutionLogsTool(final LogRepositoryInterface logRepository) {
        this.logRepository = logRepository;
    }

    @Override
    public ToolFamily family() {
        return ToolFamily.READ;
    }

    @Override
    public WritePolicy writePolicy() {
        return WritePolicy.AUTO;
    }

    @Override
    public String permission() {
        return "execution:access_logs";
    }

    @Tool(name = "read-execution-logs", value = "Read the logs of a Kestra execution as timestamped lines. `executionId` scopes to one execution; the optional per-field filters narrow the results (e.g. by task or level). Use this to diagnose why an execution failed or to summarize a run.")
    public String readExecutionLogs(
        @P(name = "executionId", value = "The id of the execution whose logs to read")String executionId,
        @QueryFilterFormat(QueryFilter.Resource.LOG) List<QueryFilter> filters
    ) {
        String tenant = AgentCallContext.requireTenant();

        // executionId is the authoritative scope; ignore any EXECUTION_ID the model added via filters.
        List<QueryFilter> effective = new ArrayList<>();
        effective.add(new QueryFilter(QueryFilter.Field.EXECUTION_ID, QueryFilter.Op.EQUALS, executionId, null, null));
        if (filters != null) {
            filters.stream()
                .filter(filter -> filter.field() != QueryFilter.Field.EXECUTION_ID)
                .forEach(effective::add);
        }

        List<LogEntry> entries = logRepository.find(PageableUtils.from(1, MAX_LOGS), tenant, effective);
        if (entries.isEmpty()) {
            return "No logs found for execution '" + executionId + "' with the given filters.";
        }

        return entries.stream()
            .map(ReadExecutionLogsTool::formatLine)
            .collect(Collectors.joining("\n"));
    }

    private static String formatLine(final LogEntry entry) {
        StringBuilder line = new StringBuilder()
            .append(entry.getTimestamp())
            .append(" [").append(entry.getLevel()).append("] ");
        if (entry.getTaskId() != null) {
            line.append(entry.getTaskId()).append(": ");
        }
        line.append(entry.getMessage());
        return line.toString();
    }
}
