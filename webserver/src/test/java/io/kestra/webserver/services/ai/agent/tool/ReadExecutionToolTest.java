package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadExecutionToolTest {
    private static final String TENANT = "main";

    private ExecutionRepositoryInterface executionRepository;
    private ReadExecutionTool tool;

    @BeforeEach
    void setUp() {
        executionRepository = mock(ExecutionRepositoryInterface.class);
        tool = new ReadExecutionTool(executionRepository);
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
    void shouldSummarizeExecutionWithTaskRunsWhenFound() {
        // Given — a FAILED execution with a successful task run and a failed one with an attempt history
        State failedState = State.of(
            State.Type.FAILED, List.of(
                new State.History(State.Type.CREATED, Instant.parse("2026-01-01T00:00:00Z")),
                new State.History(State.Type.RUNNING, Instant.parse("2026-01-01T00:00:01Z")),
                new State.History(State.Type.FAILED, Instant.parse("2026-01-01T00:00:05Z"))
            )
        );
        TaskRun okRun = TaskRun.builder()
            .taskId("extract")
            .state(
                State.of(
                    State.Type.SUCCESS, List.of(
                        new State.History(State.Type.CREATED, Instant.parse("2026-01-01T00:00:00Z")),
                        new State.History(State.Type.SUCCESS, Instant.parse("2026-01-01T00:00:01Z"))
                    )
                )
            )
            .attempts(List.of(TaskRunAttempt.builder().state(new State(State.Type.SUCCESS)).build()))
            .build();
        TaskRun failedRun = TaskRun.builder()
            .taskId("load")
            .state(failedState)
            .attempts(List.of(TaskRunAttempt.builder().state(failedState).build()))
            .build();
        Execution execution = Execution.builder()
            .id("exec-1")
            .tenantId(TENANT)
            .namespace("io.kestra.test")
            .flowId("flow-1")
            .state(failedState)
            .taskRunList(List.of(okRun, failedRun))
            .build();
        when(executionRepository.findById(TENANT, "exec-1")).thenReturn(Optional.of(execution));

        // When
        String result = tool.readExecution("exec-1", null);

        // Then — header, per-taskrun lines, and the failed run's attempt state history
        assertThat(result).contains("Execution 'exec-1' of flow io.kestra.test.flow-1");
        assertThat(result).contains("State: FAILED");
        assertThat(result).contains("duration PT5S");
        assertThat(result).contains("- extract [SUCCESS] attempts=1");
        assertThat(result).contains("- load [FAILED] attempts=1");
        assertThat(result).contains("attempt 1: CREATED@2026-01-01T00:00:00Z -> RUNNING@2026-01-01T00:00:01Z -> FAILED@2026-01-01T00:00:05Z");
    }

    @Test
    void shouldMentionNoTaskRunsWhenExecutionHasNone() {
        // Given — a freshly created execution without task runs
        Execution execution = Execution.builder()
            .id("exec-2")
            .tenantId(TENANT)
            .namespace("io.kestra.test")
            .flowId("flow-1")
            .state(new State(State.Type.CREATED))
            .build();
        when(executionRepository.findById(TENANT, "exec-2")).thenReturn(Optional.of(execution));

        // When
        String result = tool.readExecution("exec-2", null);

        // Then
        assertThat(result).contains("State: CREATED");
        assertThat(result).contains("No task runs.");
    }

    @Test
    void shouldThrowWhenExecutionNotFound() {
        // Given
        when(executionRepository.findById(TENANT, "missing")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> tool.readExecution("missing", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Execution not found: 'missing'");
    }
}
