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
class ChangeTaskRunStateToolTest {
    private static final String NAMESPACE = "io.kestra.tests";

    @Inject
    private ChangeTaskRunStateTool tool;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows({ "flows/valids/restart_last_failed.yaml" })
    void shouldRunTheTasksAfterTheTaskRunWhenItIsMarkedSuccess() throws Exception {
        // Given
        Execution failed = runFailed();
        String taskRunId = failed.findTaskRunsByTaskId("c").getFirst().getId();

        // When
        Map<String, Object> result = CatalogDispatcher.dispatch(
            tool, "change_task_run_state", """
                { "executionId": "%s", "taskRunId": "%s", "state": "SUCCESS" }""".formatted(failed.getId(), taskRunId), MAIN_TENANT
        );

        // Then
        assertThat(result).containsEntry("taskRunId", taskRunId).containsEntry("state", "SUCCESS");
        Execution rerun = runnerUtils.awaitExecution(
            execution -> execution.getState().isTerminated() && !execution.findTaskRunsByTaskId("d").isEmpty(), failed.withTenantId(MAIN_TENANT)
        );
        assertThat(rerun.findTaskRunsByTaskId("c").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(rerun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({ "flows/valids/restart_last_failed.yaml" })
    void shouldRefuseChangeWhenTaskRunIsNotInExecution() throws Exception {
        Execution failed = runFailed();

        assertThatThrownBy(
            () -> CatalogDispatcher.dispatch(
                tool, "change_task_run_state", "{ \"executionId\": \"%s\", \"taskRunId\": \"unknown\", \"state\": \"SUCCESS\" }".formatted(failed.getId()), MAIN_TENANT
            )
        ).hasMessageContaining("Task run 'unknown' not found");
    }

    private Execution runFailed() throws Exception {
        Execution failed = runnerUtils.runOne(MAIN_TENANT, NAMESPACE, "restart_last_failed", null, (BiFunction<FlowInterface, Execution, Map<String, Object>>) null);
        assertThat(failed.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        return failed;
    }
}
