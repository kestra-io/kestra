package io.kestra.plugin.core.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunnerUtils;
import jakarta.inject.Inject;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
class SwitchTest {

    @Inject
    private RunnerUtils runnerUtils;

    @Test
    @LoadFlows({"flows/valids/switch.yaml"})
    void switchFirst() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "FIRST")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("t1");
        assertThat(execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("value")).isEqualTo("FIRST");
        assertThat(execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("defaults")).isEqualTo(false);
    }

    @Test
    @LoadFlows({"flows/valids/switch.yaml"})
    void switchSecond() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "SECOND")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("t2");
        assertThat(execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("value")).isEqualTo("SECOND");
        assertThat(execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("defaults")).isEqualTo(false);
        assertThat(execution.getTaskRunList().get(2).getTaskId()).isEqualTo("t2_sub");
    }

    @Test
    @LoadFlows({"flows/valids/switch.yaml"})
    void switchThird() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "THIRD")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("t3");
        assertThat(execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("value")).isEqualTo("THIRD");
        assertThat(execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("defaults")).isEqualTo(false);
        assertThat(execution.getTaskRunList().get(2).getTaskId()).isEqualTo("failed");
        assertThat(execution.getTaskRunList().get(3).getTaskId()).isEqualTo("error-t1");
    }

    @Test
    @LoadFlows({"flows/valids/switch.yaml"})
    void switchDefault() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "DEFAULT")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId()).isEqualTo("default");
        assertThat(execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("value")).isEqualTo("DEFAULT");
        assertThat(execution.findTaskRunsByTaskId("parent-seq").getFirst().getOutputs().get("defaults")).isEqualTo(true);
    }

    @Test
    @LoadFlows({"flows/valids/switch-impossible.yaml"})
    void switchImpossible() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "switch-impossible",
            null,
            (f, e) -> ImmutableMap.of("string", "impossible")
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }
}
