package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.event.Level;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

import io.micronaut.data.model.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadExecutionLogsToolTest {
    private static final String TENANT = "main";

    private LogDataStoreInterface logRepository;
    private ReadExecutionLogsTool tool;

    @BeforeEach
    void setUp() {
        logRepository = mock(LogDataStoreInterface.class);
        tool = new ReadExecutionLogsTool(logRepository);
        AgentCallContext.set(AgentCallContext.Context.ofTenant(TENANT));
    }

    @AfterEach
    void tearDown() {
        AgentCallContext.clear();
    }

    @Test
    void shouldExposeReadOnlyMetadata() {
        // When / Then
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldReturnMatchingLogLinesWhenLogsExist() {
        // Given
        LogEntry line = LogEntry.builder()
            .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
            .level(Level.ERROR)
            .taskId("load")
            .message("boom")
            .build();
        when(logRepository.find(any(Pageable.class), eq(TENANT), any()))
            .thenReturn(new ArrayListTotal<>(List.of(line), 1));

        // When
        ReadExecutionLogsTool.Result result = tool.readExecutionLogs("exec-1", null, null);

        // Then
        assertThat(result.executionId()).isEqualTo("exec-1");
        assertThat(result.logs()).containsExactly(
            new ReadExecutionLogsTool.LogLine("2026-01-01T00:00:00Z", "ERROR", "load", "boom")
        );
    }

    @Test
    void shouldReturnEmptyLogsWhenNoneMatch() {
        // Given
        when(logRepository.find(any(Pageable.class), eq(TENANT), any()))
            .thenReturn(new ArrayListTotal<>(List.of(), 0));

        // When
        ReadExecutionLogsTool.Result result = tool.readExecutionLogs("exec-1", null, null);

        // Then
        assertThat(result.executionId()).isEqualTo("exec-1");
        assertThat(result.logs()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldForceExecutionIdScopeAndDropModelSuppliedExecutionId() {
        // Given — the model smuggles a different EXECUTION_ID alongside a real TASK_ID filter
        when(logRepository.find(any(Pageable.class), eq(TENANT), any()))
            .thenReturn(new ArrayListTotal<>(List.of(), 0));
        List<QueryFilter> modelFilters = List.of(
            new QueryFilter(QueryFilter.Field.EXECUTION_ID, QueryFilter.Op.EQUALS, "other-exec", null, null),
            new QueryFilter(QueryFilter.Field.TASK_ID, QueryFilter.Op.EQUALS, "load", null, null)
        );

        // When
        tool.readExecutionLogs("exec-1", modelFilters, null);

        // Then — exactly one EXECUTION_ID (the authoritative arg); the model's is dropped, TASK_ID kept
        ArgumentCaptor<List<QueryFilter>> captor = ArgumentCaptor.forClass(List.class);
        verify(logRepository).find(any(Pageable.class), eq(TENANT), captor.capture());
        assertThat(captor.getValue())
            .extracting(filter -> filter.field() + "/" + filter.value())
            .containsExactly("EXECUTION_ID/exec-1", "TASK_ID/load");
    }
}
