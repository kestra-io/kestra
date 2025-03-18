package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.RunnerUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@KestraTest(startRunner = true)
class FinallyTest {
    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    private FlowInputOutput flowIO;

    @Test
    @LoadFlows({"flows/valids/finally-sequential.yaml"})
    void sequentialWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-sequential", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-sequential.yaml"})
    void sequentialWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-sequential", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-sequential-error.yaml"})
    void sequentialErrorBlockWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-sequential-error", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-sequential-error-first.yaml"})
    void sequentialErrorFirst() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "finally-sequential-error-first");

        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("ok").isEmpty(), is(true));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-sequential-error.yaml"})
    void sequentialErrorBlockWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-sequential-error", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(7));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-allowfailure.yaml"})
    void allowFailureWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-allowfailure", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-allowfailure.yaml"})
    void allowFailureWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-allowfailure", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(7));
        assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-parallel.yaml"})
    void parallelWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-parallel", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(8));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-parallel.yaml"})
    void parallelWithErrors() throws QueueException, TimeoutException {
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
    }

    @Test
    @LoadFlows({"flows/valids/finally-foreach.yaml"})
    void forEachWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-foreach", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(9));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-foreach.yaml"})
    void forEachWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-foreach", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(11));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-eachparallel.yaml"})
    void eachParallelWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-eachparallel", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(9));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-eachparallel.yaml"})
    void eachParallelWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-eachparallel", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(11));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-dag.yaml"})
    void dagWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-dag", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(7));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-dag.yaml"})
    void dagWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-dag", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(9));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-flow.yaml"})
    void flowWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-flow", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-flow.yaml"})
    void flowWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-flow", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-flow-error.yaml"})
    void flowErrorBlockWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-flow-error", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-flow-error.yaml"})
    void flowErrorBlockWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests", "finally-flow-error", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList(), hasSize(6));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/finally-flow-error-first.yaml"})
    void flowErrorFirst() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "finally-flow-error-first");

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("ok").isEmpty(), is(true));
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }
}