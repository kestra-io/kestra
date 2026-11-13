package io.kestra.webserver.services.ai.agent.tool;

import java.util.Map;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.TestRunnerUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(startRunner = true)
class ForceRunExecutionToolTest {
    private static final String NAMESPACE = "io.kestra.tests";

    @Inject
    private ForceRunExecutionTool tool;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows({ "flows/valids/pause_on_resume_optional.yaml" })
    void shouldRunPastThePauseWhenPausedExecutionIsForced() throws Exception {
        // Given
        Execution paused = runnerUtils.runOneUntilPaused(MAIN_TENANT, NAMESPACE, "pause_on_resume_optional");

        // When
        Map<String, Object> result = CatalogDispatcher.dispatch(tool, "force_run_execution", "{ \"executionId\": \"%s\" }".formatted(paused.getId()), MAIN_TENANT);

        // Then
        assertThat(result).containsEntry("executionId", paused.getId());
        Execution terminated = runnerUtils.awaitExecution(execution -> execution.getState().isTerminated(), paused);
        assertThat(terminated.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({ "flows/valids/restart_last_failed.yaml" })
    void shouldRefuseForceRunWhenExecutionIsTerminated() throws Exception {
        Execution failed = runnerUtils.runOne(MAIN_TENANT, NAMESPACE, "restart_last_failed", null, (BiFunction<FlowInterface, Execution, Map<String, Object>>) null);

        assertThatThrownBy(
            () -> CatalogDispatcher.dispatch(tool, "force_run_execution", "{ \"executionId\": \"%s\" }".formatted(failed.getId()), MAIN_TENANT)
        ).hasMessageContaining("expected not terminated");
    }
}
