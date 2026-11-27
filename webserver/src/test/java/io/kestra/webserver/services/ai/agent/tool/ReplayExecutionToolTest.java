package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(startRunner = true)
class ReplayExecutionToolTest {
    private static final String NAMESPACE = "io.kestra.tests";

    @Inject
    private ReplayExecutionTool tool;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows({ "flows/valids/restart_last_failed.yaml" })
    void shouldCreateNewExecutionFromTaskRunWhenReplayed() throws Exception {
        // Given
        Execution failed = runnerUtils.runOne(MAIN_TENANT, NAMESPACE, "restart_last_failed", null, (BiFunction<FlowInterface, Execution, Map<String, Object>>) null);
        assertThat(failed.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        String taskRunId = failed.findTaskRunsByTaskId("c").getFirst().getId();

        // When
        Map<String, Object> result = CatalogDispatcher.dispatch(
            tool, "replay_execution", """
                { "executionId": "%s", "taskRunId": "%s" }""".formatted(failed.getId(), taskRunId), MAIN_TENANT
        );

        // Then
        String replayedId = (String) result.get("executionId");
        assertThat(replayedId).isNotEqualTo(failed.getId());
        assertThat(result).containsEntry("replayedFromExecutionId", failed.getId()).containsEntry("flowRevision", failed.getFlowRevision());
        Execution replayed = executionRepository.findById(MAIN_TENANT, replayedId).orElseThrow();
        assertThat(replayed.findTaskRunsByTaskId("a").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Execution original = runnerUtils.awaitExecution(
            execution -> execution.getLabels().contains(new Label(Label.REPLAYED, "true")), failed.withTenantId(MAIN_TENANT)
        );
        assertThat(original.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    void shouldRefuseReplayWhenExecutionIsNotTerminated() {
        String executionId = save(State.Type.RUNNING);

        assertThatThrownBy(
            () -> CatalogDispatcher.dispatch(tool, "replay_execution", "{ \"executionId\": \"%s\" }".formatted(executionId), MAIN_TENANT)
        ).hasMessageContaining("expected terminated");
    }

    @Test
    void shouldRefuseReplayWhenTaskRunIsNotInExecution() {
        String executionId = save(State.Type.FAILED);

        assertThatThrownBy(
            () -> CatalogDispatcher.dispatch(
                tool, "replay_execution", "{ \"executionId\": \"%s\", \"taskRunId\": \"unknown\" }".formatted(executionId), MAIN_TENANT
            )
        ).hasMessageContaining("Task run 'unknown' not found");
    }

    private String save(final State.Type stateType) {
        String executionId = IdUtils.create();
        executionRepository.save(
            Execution.builder()
                .id(executionId)
                .tenantId(MAIN_TENANT)
                .namespace(NAMESPACE)
                .flowId("flow-1")
                .flowRevision(1)
                .taskRunList(List.of())
                .state(State.of(stateType, List.of(new State.History(stateType, Instant.parse("2026-01-01T00:00:00Z")))))
                .build()
        );
        return executionId;
    }
}
