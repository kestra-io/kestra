package io.kestra.webserver.services.ai.agent.tool;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration check that {@code restart-execution} restarts a restartable execution and waits for the
 * executor to apply the restart before returning — so the outcome is observable — and rejects a missing
 * or non-restartable execution. Runs against the real executor ({@code startRunner = true}) because the
 * tool now blocks on the async restart operation, which only completes once the executor processes it.
 */
@KestraTest(startRunner = true)
class RestartExecutionToolTest {
    private static final String NAMESPACE = "io.kestra.tests";
    private static final AgentCallContext.Context CONTEXT = AgentCallContext.Context.ofTenant(MAIN_TENANT);

    @Inject
    private RestartExecutionTool tool;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    void shouldExposeActConfirmMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.ACT);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.CONFIRM);
    }

    @Test
    @LoadFlows({ "flows/valids/restart_last_failed.yaml" })
    void shouldRestartAndReturnObservableStateWhenExecutionIsRestartable() throws Exception {
        // Given — run the flow until it ends in FAILED
        Execution failedExecution = runnerUtils.runOne(
            MAIN_TENANT, NAMESPACE, "restart_last_failed", null, (BiFunction<FlowInterface, Execution, Map<String, Object>>) null
        );
        assertThat(failedExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // When — restart via the tool
        RestartExecutionTool.Result result = tool.restartExecution(failedExecution.getId(), null, CONTEXT);

        // Then — the tool waited for the executor to accept the restart and acknowledged it
        assertThat(result.executionId()).isEqualTo(failedExecution.getId());

        // And — the restart is observable: the execution carries a RESTARTED transition and finishes
        Execution restarted = runnerUtils.awaitExecution(
            execution -> execution.getState().getHistories().stream().anyMatch(it -> it.getState() == State.Type.RESTARTED)
                && execution.getState().isTerminated(),
            failedExecution.withTenantId(MAIN_TENANT),
            Duration.ofSeconds(15)
        );
        assertThat(restarted.getId()).isEqualTo(failedExecution.getId());
        assertThat(restarted.getState().getHistories().stream().anyMatch(it -> it.getState() == State.Type.RESTARTED)).isTrue();
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
