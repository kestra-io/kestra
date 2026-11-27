package io.kestra.webserver.services.ai.agent.tool;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.TestRunnerUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(startRunner = true)
class PauseExecutionToolTest {

    @Inject
    private PauseExecutionTool tool;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows({ "flows/ai-tools/sleep.yaml" })
    void shouldPauseRunningExecution() throws Exception {
        Execution running = runnerUtils.runOneUntilRunning(MAIN_TENANT, "io.kestra.tests.ai", "sleep");

        ToolCalls.call(tool, "pause_execution", """
            { "executionId": "%s" }
            """.formatted(running.getId()), MAIN_TENANT);

        Execution paused = runnerUtils.awaitExecution(execution -> execution.getState().isPaused(), running, Duration.ofSeconds(15));
        assertThat(paused.getState().getCurrent()).isEqualTo(State.Type.PAUSED);
    }

    @Test
    @LoadFlows({ "flows/ai-tools/sleep.yaml" })
    void shouldRefuseWhenExecutionIsNotRunning() throws Exception {
        Execution running = runnerUtils.runOneUntilRunning(MAIN_TENANT, "io.kestra.tests.ai", "sleep");
        ToolCalls.call(tool, "pause_execution", """
            { "executionId": "%s" }
            """.formatted(running.getId()), MAIN_TENANT);
        runnerUtils.awaitExecution(execution -> execution.getState().isPaused(), running, Duration.ofSeconds(15));

        assertThatThrownBy(() -> ToolCalls.call(tool, "pause_execution", """
            { "executionId": "%s" }
            """.formatted(running.getId()), MAIN_TENANT))
            .hasMessageContaining("expected RUNNING");
    }
}
