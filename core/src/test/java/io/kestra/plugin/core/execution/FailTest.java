package io.kestra.plugin.core.execution;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class FailTest extends AbstractMemoryRunnerTest {
    @Test
    void failOnSwitch() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "fail-on-switch", null,
            (f, e) -> Map.of("param", "fail") , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.findTaskRunsByTaskId("switch").get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void failOnCondition() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "fail-on-condition", null,
            (f, e) -> Map.of("param", "fail") , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.findTaskRunsByTaskId("fail").get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void dontFailOnCondition() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "fail-on-condition", null,
            (f, e) -> Map.of("param", "success") , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.findTaskRunsByTaskId("fail").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }
}
