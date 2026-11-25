package io.kestra.webserver.services.ai.agent.tool;

import java.time.Duration;
import java.util.Map;

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
class KillExecutionToolTest {

    @Inject
    private KillExecutionTool tool;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows({ "flows/ai-tools/sleep.yaml" })
    void shouldKillRunningExecution() throws Exception {
        Execution running = runnerUtils.runOneUntilRunning(MAIN_TENANT, "io.kestra.tests.ai", "sleep");

        Map<String, Object> result = ToolCalls.call(tool, "kill_execution", """
            { "executionId": "%s" }
            """.formatted(running.getId()), MAIN_TENANT);

        assertThat(result).containsEntry("executionId", running.getId());
        Execution killed = runnerUtils.awaitExecution(execution -> execution.getState().getCurrent() == State.Type.KILLED, running, Duration.ofSeconds(15));
        assertThat(killed.getState().getCurrent()).isEqualTo(State.Type.KILLED);
    }

    @Test
    @LoadFlows({ "flows/ai-tools/sleep.yaml" })
    void shouldRefuseWhenExecutionHasNothingLeftToKill() throws Exception {
        Execution running = runnerUtils.runOneUntilRunning(MAIN_TENANT, "io.kestra.tests.ai", "sleep");
        ToolCalls.call(tool, "kill_execution", """
            { "executionId": "%s" }
            """.formatted(running.getId()), MAIN_TENANT);
        runnerUtils.awaitExecution(execution -> execution.getState().getCurrent() == State.Type.KILLED, running, Duration.ofSeconds(15));

        assertThatThrownBy(() -> ToolCalls.call(tool, "kill_execution", """
            { "executionId": "%s", "cascade": false }
            """.formatted(running.getId()), MAIN_TENANT))
            .hasMessageContaining("cannot be killed");
    }
}
