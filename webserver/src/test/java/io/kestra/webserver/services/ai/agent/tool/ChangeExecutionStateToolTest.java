package io.kestra.webserver.services.ai.agent.tool;

import java.time.Duration;
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
class ChangeExecutionStateToolTest {

    @Inject
    private ChangeExecutionStateTool tool;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows({ "flows/valids/restart_last_failed.yaml" })
    void shouldMarkFailedExecutionAsSuccess() throws Exception {
        Execution failed = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "restart_last_failed", null, (BiFunction<FlowInterface, Execution, Map<String, Object>>) null);
        assertThat(failed.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Map<String, Object> result = ToolCalls.call(tool, "change_execution_state", """
            { "executionId": "%s", "state": "SUCCESS" }
            """.formatted(failed.getId()), MAIN_TENANT);

        assertThat(result).containsEntry("state", "SUCCESS");
        Execution changed = runnerUtils.awaitExecution(execution -> execution.getState().getCurrent() == State.Type.SUCCESS, failed, Duration.ofSeconds(15));
        assertThat(changed.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({ "flows/ai-tools/sleep.yaml" })
    void shouldRefuseWhenExecutionHasNotEnded() throws Exception {
        Execution running = runnerUtils.runOneUntilRunning(MAIN_TENANT, "io.kestra.tests.ai", "sleep");

        assertThatThrownBy(() -> ToolCalls.call(tool, "change_execution_state", """
            { "executionId": "%s", "state": "SUCCESS" }
            """.formatted(running.getId()), MAIN_TENANT))
            .hasMessageContaining("cannot be changed");
    }
}
