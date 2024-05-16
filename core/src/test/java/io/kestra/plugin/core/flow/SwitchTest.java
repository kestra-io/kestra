package io.kestra.plugin.core.flow;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class SwitchTest extends AbstractMemoryRunnerTest {
    @Test
    void switchFirst() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "FIRST")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("t1"));
        assertThat(execution.findTaskRunsByTaskId("parent-seq").get(0).getOutputs().get("value"), is("FIRST"));
        assertThat(execution.findTaskRunsByTaskId("parent-seq").get(0).getOutputs().get("defaults"), is(false));
    }

    @Test
    void switchSecond() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "SECOND")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("t2"));
        assertThat(execution.findTaskRunsByTaskId("parent-seq").get(0).getOutputs().get("value"), is("SECOND"));
        assertThat(execution.findTaskRunsByTaskId("parent-seq").get(0).getOutputs().get("defaults"), is(false));
        assertThat(execution.getTaskRunList().get(2).getTaskId(), is("t2_sub"));
    }

    @Test
    void switchThird() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "THIRD")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("t3"));
        assertThat(execution.findTaskRunsByTaskId("parent-seq").get(0).getOutputs().get("value"), is("THIRD"));
        assertThat(execution.findTaskRunsByTaskId("parent-seq").get(0).getOutputs().get("defaults"), is(false));
        assertThat(execution.getTaskRunList().get(2).getTaskId(), is("failed"));
        assertThat(execution.getTaskRunList().get(3).getTaskId(), is("error-t1"));
    }

    @Test
    void switchDefault() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "switch",
            null,
            (f, e) -> ImmutableMap.of("string", "DEFAULT")
        );

        assertThat(execution.getTaskRunList().get(1).getTaskId(), is("default"));
        assertThat(execution.findTaskRunsByTaskId("parent-seq").get(0).getOutputs().get("value"), is("DEFAULT"));
        assertThat(execution.findTaskRunsByTaskId("parent-seq").get(0).getOutputs().get("defaults"), is(true));
    }

    @Test
    void switchImpossible() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "switch-impossible",
            null,
            (f, e) -> ImmutableMap.of("string", "impossible")
        );

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}
