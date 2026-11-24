package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class DeleteExecutionToolTest {

    @Inject
    private DeleteExecutionTool tool;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    void shouldDeleteEndedExecution() {
        String executionId = save(State.Type.SUCCESS);

        ToolCalls.call(tool, "delete_execution", """
            { "executionId": "%s" }
            """.formatted(executionId), MAIN_TENANT);

        assertThat(executionRepository.findById(MAIN_TENANT, executionId)).isEmpty();
    }

    @Test
    void shouldKeepExecutionThatHasNotEnded() {
        String executionId = save(State.Type.RUNNING);

        assertThatThrownBy(() -> ToolCalls.call(tool, "delete_execution", """
            { "executionId": "%s" }
            """.formatted(executionId), MAIN_TENANT))
            .hasMessageContaining("Kill it first");

        assertThat(executionRepository.findById(MAIN_TENANT, executionId)).isPresent();
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
                .namespace("io.kestra.tests.ai")
                .flowId("flow-1")
                .state(state)
                .build()
        );
        return executionId;
    }
}
