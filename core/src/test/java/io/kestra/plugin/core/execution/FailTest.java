package io.kestra.plugin.core.execution;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.TestRunnerUtils;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class FailTest {

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows({"flows/valids/fail-on-switch.yaml"})
    void failOnSwitch() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "fail-on-switch", null,
            (f, e) -> Map.of("param", "fail") , Duration.ofSeconds(20));

        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.findTaskRunsByTaskId("switch").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @LoadFlows(value = {"flows/valids/fail-on-condition.yaml"}, tenantId = "fail")
    void failOnCondition() throws TimeoutException, QueueException{
        Execution execution = runnerUtils.runOne("fail", "io.kestra.tests", "fail-on-condition", null,
            (f, e) -> Map.of("param", "fail") , Duration.ofSeconds(20));

        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.findTaskRunsByTaskId("fail").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @LoadFlows(value = {"flows/valids/fail-on-condition.yaml"}, tenantId = "success")
    void dontFailOnCondition() throws TimeoutException, QueueException{
        Execution execution = runnerUtils.runOne("success", "io.kestra.tests", "fail-on-condition", null,
            (f, e) -> Map.of("param", "success") , Duration.ofSeconds(20));

        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.findTaskRunsByTaskId("fail").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }
}
