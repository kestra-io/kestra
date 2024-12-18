package io.kestra.plugin.core.execution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.RunnerUtils;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class FailTest {

    @Inject
    private RunnerUtils runnerUtils;

    @Test
    @LoadFlows({"flows/valids/fail-on-switch.yaml"})
    void failOnSwitch() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "fail-on-switch", null,
            (f, e) -> Map.of("param", "fail") , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.findTaskRunsByTaskId("switch").getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    @LoadFlows({"flows/valids/fail-on-condition.yaml"})
    void failOnCondition() throws TimeoutException, QueueException{
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "fail-on-condition", null,
            (f, e) -> Map.of("param", "fail") , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.findTaskRunsByTaskId("fail").getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    @LoadFlows({"flows/valids/fail-on-condition.yaml"})
    void dontFailOnCondition() throws TimeoutException, QueueException{
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "fail-on-condition", null,
            (f, e) -> Map.of("param", "success") , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.findTaskRunsByTaskId("fail").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }
}
