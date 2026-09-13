package io.kestra.webserver.services.ai.agent.tool;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.utils.PageableUtils;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ReadExecutionLogsTool implements AiPlatformTool {
    private static final int MAX_LOGS = 500;

    private final LogDataStoreInterface logRepository;

    @Inject
    public ReadExecutionLogsTool(final LogDataStoreInterface logRepository) {
        this.logRepository = logRepository;
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
        name = "read-execution-logs",
        value = "Read the logs of a Kestra execution. `executionId` scopes to one execution; the optional per-field filters narrow the results (e.g. by task or level). Use this to diagnose why an execution failed or to summarize a run. "
            + "Returns an object { executionId, logs } where `logs` is an array of { timestamp, level, taskId, message } (empty when nothing matches); `taskId` is null for execution-level logs."
    )
    public Result readExecutionLogs(
        @P(name = "executionId", value = "The id of the execution whose logs to read") String executionId,
        @QueryFilterFormat(QueryFilter.Resource.LOG) List<QueryFilter> filters,
        final AgentCallContext.Context context) {
        String tenant = context.tenant();

        // executionId is the authoritative scope; ignore any EXECUTION_ID the model added via filters.
        List<QueryFilter> effective = new ArrayList<>();
        effective.add(new QueryFilter(QueryFilter.Field.EXECUTION_ID, QueryFilter.Op.EQUALS, executionId, null, null));
        if (filters != null) {
            filters.stream()
                .filter(filter -> filter.field() != QueryFilter.Field.EXECUTION_ID)
                .forEach(effective::add);
        }

        List<LogEntry> entries = logRepository.find(PageableUtils.from(1, MAX_LOGS), tenant, effective).getContent();

        return new Result(
            executionId, entries.stream()
                .map(ReadExecutionLogsTool::toLogLine)
                .toList()
        );
    }

    private static LogLine toLogLine(final LogEntry entry) {
        return new LogLine(
            String.valueOf(entry.getTimestamp()),
            String.valueOf(entry.getLevel()),
            entry.getTaskId(),
            entry.getMessage()
        );
    }

    public record Result(String executionId, List<LogLine> logs) {
    }

    public record LogLine(String timestamp, String level, String taskId, String message) {
    }
}
