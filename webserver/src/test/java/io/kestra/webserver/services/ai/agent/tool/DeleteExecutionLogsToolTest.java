package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.utils.IdUtils;

import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(environments = "memory")
class DeleteExecutionLogsToolTest {
    private static final String TENANT = "deleteexecutionlogs";
    private static final String NAMESPACE = "io.kestra.test.ai";

    @Inject
    private DeleteExecutionLogsTool tool;

    @Inject
    private LogDataStoreInterface logRepository;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    void shouldDeleteOnlyTheTargetedLogsWhenTaskAndLevelGiven() {
        // Given — an execution with logs of two tasks at several levels, and another execution
        String target = createExecution();
        String other = createExecution();
        logRepository.save(logEntry(target, "load", Level.ERROR, "load error"));
        logRepository.save(logEntry(target, "load", Level.WARN, "load warn"));
        logRepository.save(logEntry(target, "load", Level.INFO, "load info"));
        logRepository.save(logEntry(target, "transform", Level.ERROR, "transform error"));
        logRepository.save(logEntry(other, "load", Level.ERROR, "other error"));

        // When
        var result = ToolCalls.call(tool, "delete_execution_logs", """
            { "executionId": "%s", "taskId": "load", "minLevel": "WARN" }""".formatted(target), TENANT);

        // Then
        assertThat(result.get("executionId")).isEqualTo(target);
        assertThat(messages(target)).containsExactlyInAnyOrder("load info", "transform error");
        assertThat(messages(other)).containsExactly("other error");
    }

    @Test
    void shouldRefuseWhenExecutionDoesNotExist() {
        String missing = IdUtils.create();
        logRepository.save(logEntry(missing, "load", Level.ERROR, "orphan"));

        assertThatThrownBy(() -> ToolCalls.call(tool, "delete_execution_logs", """
            { "executionId": "%s" }""".formatted(missing), TENANT))
            .hasMessageContaining("Execution not found");
        assertThat(messages(missing)).containsExactly("orphan");
    }

    private String createExecution() {
        String executionId = IdUtils.create();
        executionRepository.save(
            Execution.builder()
                .id(executionId)
                .tenantId(TENANT)
                .namespace(NAMESPACE)
                .flowId("flow-1")
                .state(new State())
                .build()
        );
        return executionId;
    }

    private List<String> messages(final String executionId) {
        return logRepository.find(
            Pageable.from(1, 50), TENANT,
            List.of(new QueryFilter(QueryFilter.Field.EXECUTION_ID, QueryFilter.Op.EQUALS, executionId, null, null))
        ).getContent().stream().map(LogEntry::getMessage).toList();
    }

    private static LogEntry logEntry(final String executionId, final String taskId, final Level level, final String message) {
        return LogEntry.builder()
            .tenantId(TENANT)
            .namespace(NAMESPACE)
            .flowId("flow-1")
            .executionId(executionId)
            .taskId(taskId)
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .level(level)
            .message(message)
            .build();
    }
}
