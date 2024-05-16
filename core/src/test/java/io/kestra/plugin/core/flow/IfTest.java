package io.kestra.plugin.core.flow;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class IfTest  extends AbstractMemoryRunnerTest {
    @Test
    void ifTruthy() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "if-condition", null,
            (f, e) -> Map.of("param", true) , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.findTaskRunsByTaskId("when-true").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        execution = runnerUtils.runOne(null, "io.kestra.tests", "if-condition", null,
            (f, e) -> Map.of("param", "true") , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.findTaskRunsByTaskId("when-true").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        execution = runnerUtils.runOne(null, "io.kestra.tests", "if-condition", null,
            (f, e) -> Map.of("param", 1) , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.findTaskRunsByTaskId("when-true").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void ifFalsy() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "if-condition", null,
            (f, e) -> Map.of("param", false) , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.findTaskRunsByTaskId("when-false").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        execution = runnerUtils.runOne(null, "io.kestra.tests", "if-condition", null,
            (f, e) -> Map.of("param", "false") , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.findTaskRunsByTaskId("when-false").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        execution = runnerUtils.runOne(null, "io.kestra.tests", "if-condition", null,
            (f, e) -> Map.of("param", 0) , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.findTaskRunsByTaskId("when-false").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        execution = runnerUtils.runOne(null, "io.kestra.tests", "if-condition", null,
            (f, e) -> Map.of("param", -0) , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.findTaskRunsByTaskId("when-false").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        // We cannot test null as inputs cannot be null
    }

    @Test
    void ifWithoutElse() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "if-without-else", null,
            (f, e) -> Map.of("param", true) , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.findTaskRunsByTaskId("when-true").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        execution = runnerUtils.runOne(null, "io.kestra.tests", "if-without-else", null,
            (f, e) -> Map.of("param", false) , Duration.ofSeconds(120));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.findTaskRunsByTaskId("when-true").isEmpty(), is(true));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }
}