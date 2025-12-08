package io.kestra.plugin.core.flow;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.TestRunnerUtils;
import jakarta.inject.Inject;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
class SwitchTest {

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows(value = {"flows/valids/switch.yaml"}, tenantId = "switch")
    void switchFirst() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            "switch",
            "io.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "FIRST")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("t1");
        assertThat(execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("value")).isEqualTo("FIRST");
        assertThat((Boolean) execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("defaults")).isEqualTo(false);
    }

    @Test
    @LoadFlows(value = {"flows/valids/switch.yaml"}, tenantId = "second")
    void switchSecond() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            "second",
            "io.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "SECOND")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("t2");
        assertThat(execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("value")).isEqualTo("SECOND");
        assertThat((Boolean) execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("defaults")).isFalse();
        assertThat(execution.getTaskRunList().get(2).getTaskId()).isEqualTo("t2_sub");
    }

    @Test
    @LoadFlows(value = {"flows/valids/switch.yaml"}, tenantId = "third")
    void switchThird() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            "third",
            "io.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "THIRD")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("t3");
        assertThat(execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("value")).isEqualTo("THIRD");
        assertThat((Boolean) execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("defaults")).isFalse();
        assertThat(execution.getTaskRunList().get(2).getTaskId()).isEqualTo("failed");
        assertThat(execution.getTaskRunList().get(3).getTaskId()).isEqualTo("error-t1");
    }

    @Test
    @LoadFlows({"flows/valids/switch.yaml"})
    void switchDefault() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "DEFAULT")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("default");
        assertThat(execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("value")).isEqualTo("DEFAULT");
        assertThat((Boolean)execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("defaults")).isTrue();
    }

    @Test
    @LoadFlows({"flows/valids/switch-impossible.yaml"})
    void switchImpossible() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests",
            "switch-impossible",
            null,
            (f, e) -> ImmutableMap.of("string", "impossible")
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @ExecuteFlow("flows/valids/switch-in-concurrent-loop.yaml")
    void switchInConcurrentLoop(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(5);
        // we check that OOMCRM_EB_DD_000 and OOMCRM_EB_DD_001 have been processed once
        assertThat(execution.getTaskRunList().stream().filter(t -> t.getTaskId().equals("OOMCRM_EB_DD_000")).count()).isEqualTo(1);
        assertThat(execution.getTaskRunList().stream().filter(t -> t.getTaskId().equals("OOMCRM_EB_DD_001")).count()).isEqualTo(1);
    }
}
