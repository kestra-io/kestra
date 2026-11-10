package io.kestra.webserver.services.ai.agent.tool;

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
public class SearchLogsTool implements McpPlatformTool {
    private static final int PAGE_SIZE = 50;

    private final LogDataStoreInterface logRepository;

    @Inject
    public SearchLogsTool(final LogDataStoreInterface logRepository) {
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
        name = "search_logs",
        value = "Search Kestra logs across flows, executions and triggers with per-field filters (e.g. a text QUERY, NAMESPACE, FLOW_ID, LEVEL, START_DATE), newest first, " + PAGE_SIZE + " per page. "
            + "Read-only; use this to find where an error occurred, then read_execution_logs to read one execution's logs in order. "
            + "Returns an object { logs, total, page } where `logs` is an array of { timestamp, level, namespace, flowId, executionId, taskId, message } (empty when nothing matches); `executionId` and `taskId` are null for logs not emitted by a task run, "
            + "and messages longer than `maxMessageLength` characters (default " + LogToolPages.DEFAULT_MAX_MESSAGE_LENGTH + ") are truncated. `total` is the number of all matching log lines, or null when the log store cannot count them; when it exceeds what was read so far, request the next `page`."
    )
    public Result searchLogs(
        @QueryFilterFormat(QueryFilter.Resource.LOG) List<QueryFilter> filters,
        @P(name = "page", value = "The 1-based page of log lines to read; omit for the first page", required = false)Integer page,
        @P(name = "maxMessageLength", value = "The maximum number of characters to return per log message, longer messages are cut and end with an ellipsis; omit for the default of " + LogToolPages.DEFAULT_MAX_MESSAGE_LENGTH, required = false) Integer maxMessageLength,
        final AgentCallContext.Context context) {
        int pageNumber = ToolPages.pageNumber(page);
        int messageLength = LogToolPages.maxMessageLength(maxMessageLength);
        Page<LogEntry> entries = LogToolPages.find(logRepository, context.tenant(), filters, pageNumber, PAGE_SIZE, "timestamp:desc");

        return new Result(
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
            entry.getNamespace(),
            entry.getFlowId(),
            entry.getExecutionId(),
            entry.getTaskId(),
            LogToolPages.truncate(entry.getMessage(), maxMessageLength)
        );
    }

    public record Result(List<LogLine> logs, Long total, int page) {
    }

    public record LogLine(String timestamp, String level, String namespace, String flowId, String executionId, String taskId, String message) {
    }
}
