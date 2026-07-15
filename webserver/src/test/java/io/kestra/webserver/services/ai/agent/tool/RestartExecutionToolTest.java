package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration check that {@code restart-execution} enqueues a restart command for a restartable
 * execution, and rejects a missing or non-restartable execution, exercising the tool → repository →
 * queue wiring with the real beans.
 */
@KestraTest(environments = "memory")
class RestartExecutionToolTest {
    private static final String NAMESPACE = "io.kestra.test.ai";
    private static final AgentCallContext.Context CONTEXT = AgentCallContext.Context.ofTenant(MAIN_TENANT);

    @Inject
    private RestartExecutionTool tool;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    void shouldExposeActConfirmMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.ACT);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.CONFIRM);
    }

    @Test
    void shouldEmitRestartCommandWhenExecutionIsRestartable() {
        // Given — a terminated (FAILED) execution can be restarted
        String executionId = save(State.Type.FAILED);

        // When
        RestartExecutionTool.Result result = tool.restartExecution(executionId, null, CONTEXT);

        // Then — the tool reports the restart with an operation id
        assertThat(result.executionId()).isEqualTo(executionId);
        assertThat(result.operationId()).isNotNull();
    }

    @Test
    void shouldThrowWhenExecutionNotFound() {
        assertThatThrownBy(() -> tool.restartExecution("does-not-exist", null, CONTEXT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Execution not found");
    }

    @Test
    void shouldThrowWhenStateIsNotRestartable() {
        // Given — a RUNNING execution is neither terminated nor paused
        String executionId = save(State.Type.RUNNING);

        // When / Then
        assertThatThrownBy(() -> tool.restartExecution(executionId, null, CONTEXT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cannot be restarted");
    }

    private String save(final State.Type stateType) {
        String executionId = IdUtils.create();
        State state = State.of(
            stateType, List.of(
                new State.History(State.Type.CREATED, Instant.parse("2026-01-01T00:00:00Z")),
                new State.History(stateType, Instant.parse("2026-01-01T00:00:05Z"))
            )
        );
        executionRepository.save(
            Execution.builder()
                .id(executionId)
                .tenantId(MAIN_TENANT)
                .namespace(NAMESPACE)
                .flowId("flow-1")
                .state(state)
                .build()
        );
        return executionId;
    }
}
