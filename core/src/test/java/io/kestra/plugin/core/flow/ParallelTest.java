package io.kestra.plugin.core.flow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.RunnerUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@KestraTest(startRunner = true)
class ParallelTest {
    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    private FlowInputOutput flowIO;

    @Test
    @ExecuteFlow("flows/valids/parallel.yaml")
    void parallel(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @Test
    @ExecuteFlow("flows/valids/parallel-nested.yaml")
    void parallelNested(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(11));
    }

    @Test
    @LoadFlows({"flows/valids/finally-parallel.yaml"})
    void errors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-parallel", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(10));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getStartDate().isAfter(execution.findTaskRunsByTaskId("a1").getFirst().getState().getEndDate().orElseThrow()), is(true));
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getStartDate().isAfter(execution.findTaskRunsByTaskId("e1").getFirst().getState().getEndDate().orElseThrow()), is(true));
    }
}
