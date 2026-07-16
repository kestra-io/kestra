package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(environments = "memory")
@Property(name = "kestra.server-type", value = "STANDALONE")
class ReadExecutionLogsToolTest {
    private static final String NAMESPACE = "io.kestra.test.ai";
    private static final AgentCallContext.Context CONTEXT = AgentCallContext.Context.ofTenant(MAIN_TENANT);

    @Inject
    private ReadExecutionLogsTool tool;

    @Inject
    private LogDataStoreInterface logRepository;

    @AfterEach
    void tearDown() {
        logRepository.deleteByQuery(MAIN_TENANT, null, null, null, (Level) null, null);
    }

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldReturnMatchingLogLinesWhenLogsExist() {
        // Given — a log line for the target execution
        String executionId = IdUtils.create();
        logRepository.save(logEntry(executionId, "load", Level.ERROR, "boom"));

        // When
        ReadExecutionLogsTool.Result result = tool.readExecutionLogs(executionId, null, CONTEXT);

        // Then
        assertThat(result.executionId()).isEqualTo(executionId);
        assertThat(result.logs())
            .anyMatch(line -> "ERROR".equals(line.level()) && "load".equals(line.taskId()) && "boom".equals(line.message()));
    }

    @Test
    void shouldReturnEmptyLogsWhenNoneMatch() {
        String executionId = IdUtils.create();

        ReadExecutionLogsTool.Result result = tool.readExecutionLogs(executionId, null, CONTEXT);

        assertThat(result.executionId()).isEqualTo(executionId);
        assertThat(result.logs()).isEmpty();
    }

    @Test
    void shouldForceExecutionIdScopeAndDropModelSuppliedExecutionId() {
        // Given — two executions each with their own log line
        String target = IdUtils.create();
        String other = IdUtils.create();
        logRepository.save(logEntry(target, "load", Level.INFO, "target log"));
        logRepository.save(logEntry(other, "load", Level.INFO, "other log"));

        // When — the model smuggles the other execution's id alongside a real task filter
        List<QueryFilter> modelFilters = List.of(
            new QueryFilter(QueryFilter.Field.EXECUTION_ID, QueryFilter.Op.EQUALS, other, null, null),
            new QueryFilter(QueryFilter.Field.TASK_ID, QueryFilter.Op.EQUALS, "load", null, null)
        );
        ReadExecutionLogsTool.Result result = tool.readExecutionLogs(target, modelFilters, CONTEXT);

        // Then — only the authoritative execution's logs come back; the other execution never leaks
        assertThat(result.executionId()).isEqualTo(target);
        assertThat(result.logs()).isNotEmpty();
        assertThat(result.logs()).allMatch(line -> "target log".equals(line.message()));
    }

    private LogEntry logEntry(final String executionId, final String taskId, final Level level, final String message) {
        return LogEntry.builder()
            .tenantId(MAIN_TENANT)
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
