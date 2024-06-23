package io.kestra.plugin.core.flow;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.runners.FlowInputOutput;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class AllowFailureTest extends AbstractMemoryRunnerTest {
    @Inject
    private FlowInputOutput flowIO;

    @Test
    void success() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "allow-failure", Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(9));
        control(execution);
        assertThat(execution.findTaskRunsByTaskId("global-error").size(), is(0));
        assertThat(execution.findTaskRunsByTaskId("last").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
    }

    @Test
    void failed() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "allow-failure",
            null,
            (f, e) -> flowIO.typedInputs(f, e, ImmutableMap.of("crash", "1"))
        );

        assertThat(execution.getTaskRunList(), hasSize(10));
        control(execution);
        assertThat(execution.findTaskRunsByTaskId("global-error").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("switch").get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("crash").get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    private static void control(Execution execution) {
        assertThat(execution.findTaskRunsByTaskId("first").get(0).getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.findTaskRunsByTaskId("1-1-allow-failure").get(0).getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.findTaskRunsByTaskId("1-1-1_seq").get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("1-1-1-1").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("ko").get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("local-error").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("1-2-todo").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
    }
}