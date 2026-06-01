package io.kestra.executor.handler;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.executor.ExecutionStateStore;
import io.kestra.executor.ExecutorContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the kill-switch guard in {@link ExecutionKilledExecutionMessageHandler}.
 *
 * <p>Only IGNORE is filtered here — KILL and CANCEL still need to process the kill event.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutionKilledExecutionMessageHandlerKillSwitchTest {

    @Mock KillSwitchService killSwitchService;
    @Mock ExecutionStateStore executionStateStore;

    ExecutionKilledExecutionMessageHandler handler;
    ExecutionKilledExecution message;

    @BeforeEach
    void setUp() throws Exception {
        handler = new ExecutionKilledExecutionMessageHandler();
        setField("killSwitchService", killSwitchService);
        setField("executionStateStore", executionStateStore);

        message = ExecutionKilledExecution.builder()
            .executionId("exec-1")
            .tenantId("tenant")
            .isOnKillCascade(false)
            .build();
    }

    @Test
    void shouldReturnEmptyWhenKillSwitchIsIgnore() {
        // Given
        when(killSwitchService.evaluate("exec-1")).thenReturn(EvaluationType.IGNORE);

        // When
        Optional<ExecutorContext> result = handler.handle(message);

        // Then — execution is ignored, not killed
        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotCallLockWhenKillSwitchIsIgnore() {
        // Given
        when(killSwitchService.evaluate("exec-1")).thenReturn(EvaluationType.IGNORE);

        // When
        handler.handle(message);

        // Then — lock is never attempted for an ignored execution
        verify(executionStateStore, never()).lock(any(), any());
    }

    private void setField(String fieldName, Object value) throws Exception {
        var field = ExecutionKilledExecutionMessageHandler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(handler, value);
    }
}
