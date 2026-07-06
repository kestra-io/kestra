package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.ToolFamily;
import io.kestra.webserver.services.ai.agent.domain.WritePolicy;

import io.micronaut.data.model.Pageable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.event.Level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadExecutionLogsToolTest {
    private static final String TENANT = "main";

    private LogRepositoryInterface logRepository;
    private ReadExecutionLogsTool tool;

    @BeforeEach
    void setUp() {
        logRepository = mock(LogRepositoryInterface.class);
        tool = new ReadExecutionLogsTool(logRepository);
        AgentCallContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        AgentCallContext.clear();
    }

    @Test
    void shouldExposeReadOnlyMetadata() {
        // When / Then
        assertThat(tool.family()).isEqualTo(ToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(WritePolicy.AUTO);
        assertThat(tool.permission()).isEqualTo("execution:access_logs");
    }

    @Test
    void shouldFormatMatchingLogLinesWhenLogsExist() {
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
        String result = tool.readExecutionLogs("exec-1", null);

        // Then
        assertThat(result).isEqualTo("2026-01-01T00:00:00Z [ERROR] load: boom");
    }

    @Test
    void shouldReturnNoLogsMessageWhenEmpty() {
        // Given
        when(logRepository.find(any(Pageable.class), eq(TENANT), any()))
            .thenReturn(new ArrayListTotal<>(List.of(), 0));

        // When
        String result = tool.readExecutionLogs("exec-1", null);

        // Then
        assertThat(result).isEqualTo("No logs found for execution 'exec-1' with the given filters.");
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
        tool.readExecutionLogs("exec-1", modelFilters);

        // Then — exactly one EXECUTION_ID (the authoritative arg); the model's is dropped, TASK_ID kept
        ArgumentCaptor<List<QueryFilter>> captor = ArgumentCaptor.forClass(List.class);
        verify(logRepository).find(any(Pageable.class), eq(TENANT), captor.capture());
        assertThat(captor.getValue())
            .extracting(filter -> filter.field() + "/" + filter.value())
            .containsExactly("EXECUTION_ID/exec-1", "TASK_ID/load");
    }
}
