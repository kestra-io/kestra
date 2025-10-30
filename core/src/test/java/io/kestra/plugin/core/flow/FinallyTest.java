package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.TestRunnerUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class FinallyTest {

    public static final String NAMESPACE = "io.kestra.tests";
    private static final String TENANT_ID = "tenant1";
    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    private FlowInputOutput flowIO;

    @Test
    @LoadFlows({"flows/valids/finally-sequential.yaml"})
    void sequentialWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            NAMESPACE, "finally-sequential", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows(value = {"flows/valids/finally-sequential.yaml"}, tenantId = TENANT_ID)
    void sequentialWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            TENANT_ID,
            NAMESPACE, "finally-sequential", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/finally-sequential-error.yaml"})
    void sequentialErrorBlockWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            NAMESPACE, "finally-sequential-error", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/finally-sequential-error-first.yaml"})
    void sequentialErrorFirst() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, NAMESPACE, "finally-sequential-error-first");

        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ok").isEmpty()).isTrue();
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows(value = {"flows/valids/finally-sequential-error.yaml"}, tenantId = TENANT_ID)
    void sequentialErrorBlockWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            TENANT_ID,
            NAMESPACE, "finally-sequential-error", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(7);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/finally-allowfailure.yaml"})
    void allowFailureWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            NAMESPACE, "finally-allowfailure", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows(value = {"flows/valids/finally-allowfailure.yaml"}, tenantId = TENANT_ID)
    void allowFailureWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            TENANT_ID,
            NAMESPACE, "finally-allowfailure", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(7);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/finally-parallel.yaml"})
    void parallelWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            NAMESPACE, "finally-parallel", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(8);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows(value = {"flows/valids/finally-parallel.yaml"}, tenantId = TENANT_ID)
    void parallelWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            TENANT_ID,
            NAMESPACE, "finally-parallel", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(10);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows(value = {"flows/valids/finally-foreach.yaml"}, tenantId = TENANT_ID)
    void forEachWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            TENANT_ID,
            NAMESPACE, "finally-foreach", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(9);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/finally-foreach.yaml"})
    void forEachWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            NAMESPACE, "finally-foreach", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(11);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/finally-eachparallel.yaml"})
    void eachParallelWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            NAMESPACE, "finally-eachparallel", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(9);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows(value = {"flows/valids/finally-eachparallel.yaml"}, tenantId = TENANT_ID)
    void eachParallelWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            TENANT_ID,
            NAMESPACE, "finally-eachparallel", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(11);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows(value = {"flows/valids/finally-dag.yaml"}, tenantId = TENANT_ID)
    void dagWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            TENANT_ID,
            NAMESPACE, "finally-dag", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(7);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/finally-dag.yaml"})
    void dagWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            NAMESPACE, "finally-dag", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(9);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/finally-flow.yaml"})
    void flowWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            NAMESPACE, "finally-flow", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(4);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows(value = {"flows/valids/finally-flow.yaml"}, tenantId = TENANT_ID)
    void flowWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            TENANT_ID,
            NAMESPACE, "finally-flow", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(4);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/finally-flow-error.yaml"})
    void flowErrorBlockWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            NAMESPACE, "finally-flow-error", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(4);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("ok").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows(value = {"flows/valids/finally-flow-error.yaml"}, tenantId = TENANT_ID)
    void flowErrorBlockWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            TENANT_ID,
            NAMESPACE, "finally-flow-error", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(20)
        );

        assertThat(execution.getTaskRunList()).hasSize(6);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/finally-flow-error-first.yaml"})
    void flowErrorFirst() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, NAMESPACE, "finally-flow-error-first");

        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ok").isEmpty()).isTrue();
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }
}