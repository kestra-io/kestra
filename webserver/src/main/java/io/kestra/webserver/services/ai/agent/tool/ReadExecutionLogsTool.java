package io.kestra.webserver.services.ai.agent.tool;

import java.util.ArrayList;
import java.util.List;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.mcp.models.McpToolAnnotations;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import io.micronaut.data.model.Page;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ReadExecutionLogsTool implements McpPlatformTool {
    private static final int PAGE_SIZE = 200;

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

    @Override
    public McpToolAnnotations annotations() {
        return McpToolAnnotations.READ_ONLY;
    }

    @Tool(
        name = "read_execution_logs",
        value = "Read the logs of a Kestra execution, oldest first, " + PAGE_SIZE + " per page. `executionId` scopes to one execution; the optional per-field filters narrow the results (e.g. by task or level). Use this to diagnose why an execution failed or to summarize a run; "
            + "to follow a running execution, read the next page again later. "
            + "Returns an object { executionId, logs, total, page } where `logs` is an array of { timestamp, level, taskId, message } (empty when nothing matches); `taskId` is null for execution-level logs and messages longer than `maxMessageLength` characters (default "
            + LogToolPages.DEFAULT_MAX_MESSAGE_LENGTH + ") are truncated. `total` is the number of all matching log lines, or null when the log store cannot count them; when it exceeds what was read so far, request the next `page`."
    )
    public Result readExecutionLogs(
        @P(name = "executionId", value = "The id of the execution whose logs to read") String executionId,
        @QueryFilterFormat(QueryFilter.Resource.LOG) List<QueryFilter> filters,
        @P(name = "page", value = "The 1-based page of log lines to read; omit for the first page", required = false) Integer page,
        @P(name = "maxMessageLength", value = "The maximum number of characters to return per log message, longer messages are cut and end with an ellipsis; omit for the default of " + LogToolPages.DEFAULT_MAX_MESSAGE_LENGTH, required = false) Integer maxMessageLength,
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

        int pageNumber = ToolPages.pageNumber(page);
        int messageLength = LogToolPages.maxMessageLength(maxMessageLength);
        Page<LogEntry> entries = LogToolPages.find(logRepository, tenant, effective, pageNumber, PAGE_SIZE, "timestamp:asc");

        return new Result(
            executionId,
            entries.getContent().stream()
                .map(entry -> toLogLine(entry, messageLength))
                .toList(),
            LogToolPages.total(logRepository, entries),
            pageNumber
        );
    }

    private static LogLine toLogLine(final LogEntry entry, final int maxMessageLength) {
        return new LogLine(
            String.valueOf(entry.getTimestamp()),
            String.valueOf(entry.getLevel()),
            entry.getTaskId(),
            LogToolPages.truncate(entry.getMessage(), maxMessageLength)
        );
    }

    public record Result(String executionId, List<LogLine> logs, Long total, int page) {
    }

    public record LogLine(String timestamp, String level, String taskId, String message) {
    }
}
