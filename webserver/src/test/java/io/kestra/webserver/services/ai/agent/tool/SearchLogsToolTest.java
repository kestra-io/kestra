package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(environments = "memory")
class SearchLogsToolTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Inject
    private SearchLogsTool tool;

    @Inject
    private LogDataStoreInterface logRepository;

    @Test
    void shouldReturnNewestMatchingLogsFirstWithTotalWhenFilteredPerField() {
        // Given — more ERROR lines in the target namespace than a page holds, plus lines the filters exclude
        String tenant = "searchlogspaging";
        String namespace = "io.kestra.test.ai.search";
        for (int i = 0; i < 52; i++) {
            logRepository.save(logEntry(tenant, namespace, Level.ERROR, "error " + i, START.plusSeconds(i)));
        }
        logRepository.save(logEntry(tenant, namespace, Level.INFO, "info", START.plusSeconds(100)));
        logRepository.save(logEntry(tenant, "io.kestra.test.ai.other", Level.ERROR, "other namespace", START.plusSeconds(100)));
        String filters = """
            "NAMESPACE": { "operator": "EQUALS", "value": "%s" }, "LEVEL": { "operator": "GREATER_THAN_OR_EQUAL_TO", "value": "ERROR" }""".formatted(namespace);

        // When — the first and second pages are requested through the catalog
        Map<String, Object> first = ToolCalls.call(tool, "search_logs", "{ %s }".formatted(filters), tenant);
        Map<String, Object> second = ToolCalls.call(tool, "search_logs", "{ %s, \"page\": 2 }".formatted(filters), tenant);

        // Then — newest first, one page at a time, with the total of every match
        assertThat(((Number) first.get("total")).longValue()).isEqualTo(52);
        assertThat(messages(first)).hasSize(50).startsWith("error 51");
        assertThat(messages(second)).containsExactly("error 1", "error 0");
    }

    @Test
    void shouldTruncateMessageWhenLongerThanDefaultLimit() {
        String tenant = "searchlogstruncate";
        logRepository.save(logEntry(tenant, "io.kestra.test.ai", Level.ERROR, "x".repeat(5000), START));

        Map<String, Object> result = ToolCalls.call(tool, "search_logs", "{}", tenant);

        assertThat(messages(result)).singleElement()
            .satisfies(message -> assertThat(message).hasSize(LogToolPages.DEFAULT_MAX_MESSAGE_LENGTH + 1).endsWith("…"));
    }

    @Test
    void shouldTruncateMessageToRequestedLengthWhenMaxMessageLengthIsGiven() {
        String tenant = "searchlogscustomtruncate";
        logRepository.save(logEntry(tenant, "io.kestra.test.ai", Level.ERROR, "x".repeat(5000), START));

        Map<String, Object> result = ToolCalls.call(tool, "search_logs", "{ \"maxMessageLength\": 100 }", tenant);

        assertThat(messages(result)).singleElement()
            .satisfies(message -> assertThat(message).hasSize(101).endsWith("…"));
    }

    @Test
    void shouldRejectMaxMessageLengthWhenBelowOne() {
        assertThatThrownBy(() -> tool.searchLogs(null, null, 0, AgentCallContext.Context.ofTenant("searchlogsbadtruncate")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxMessageLength must be 1 or greater");
    }

    private static List<String> messages(final Map<String, Object> result) {
        return ((List<?>) result.get("logs")).stream()
            .map(line -> (String) ((Map<?, ?>) line).get("message"))
            .toList();
    }

    private static LogEntry logEntry(final String tenant, final String namespace, final Level level, final String message, final Instant timestamp) {
        return LogEntry.builder()
            .tenantId(tenant)
            .namespace(namespace)
            .flowId("flow-1")
            .executionId(IdUtils.create())
            .taskId("task")
            .timestamp(timestamp)
            .level(level)
            .message(message)
            .build();
    }
}
