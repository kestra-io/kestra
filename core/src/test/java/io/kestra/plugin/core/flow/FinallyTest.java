package io.kestra.plugin.core.flow;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.utils.Await;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.TestRunnerUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class FinallyTest {

    public static final String NAMESPACE = "io.kestra.tests";
    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    private FlowInputOutput flowIO;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    @LoadFlows(value = { "flows/valids/finally-sequential.yaml" }, tenantId = "sequentialwithouterrors")
    void sequentialWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "sequentialwithouterrors",
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
    @LoadFlows(value = { "flows/valids/finally-sequential.yaml" }, tenantId = "sequentialwitherrors")
    void sequentialWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "sequentialwitherrors",
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
    @LoadFlows(value = { "flows/valids/finally-sequential-error.yaml" }, tenantId = "sequentialerrorblockwithouterrors")
    void sequentialErrorBlockWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "sequentialerrorblockwithouterrors",
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
    @LoadFlows(value = { "flows/valids/finally-sequential-error-first.yaml" }, tenantId = "sequentialerrorfirst")
    void sequentialErrorFirst() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne("sequentialerrorfirst", NAMESPACE, "finally-sequential-error-first");

        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ok").isEmpty()).isTrue();
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows(value = { "flows/valids/finally-sequential-error.yaml" }, tenantId = "sequentialerrorblockwitherrors")
    void sequentialErrorBlockWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "sequentialerrorblockwitherrors",
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
    @LoadFlows(value = { "flows/valids/finally-allowfailure.yaml" }, tenantId = "allowfailurewithouterrors")
    void allowFailureWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "allowfailurewithouterrors",
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
    @LoadFlows(value = { "flows/valids/finally-allowfailure.yaml" }, tenantId = "allowfailurewitherrors")
    void allowFailureWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "allowfailurewitherrors",
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
    @LoadFlows(value = { "flows/valids/finally-parallel.yaml" }, tenantId = "parallelwithouterrors")
    void parallelWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "parallelwithouterrors",
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
    @LoadFlows(value = { "flows/valids/finally-parallel.yaml" }, tenantId = "parallelwitherrors")
    void parallelWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "parallelwitherrors",
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
    @LoadFlows(value = {"flows/valids/finally-loop.yaml"}, tenantId = "loopwithouterrors")
    void loopWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "loopwithouterrors",
            NAMESPACE, "finally-loop", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        var subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions.size()).isEqualTo(3);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("ok").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows(value = {"flows/valids/finally-loop.yaml"}, tenantId = "loopwitherrors")
    void loopWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "loopwitherrors",
            NAMESPACE, "finally-loop", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // With transmitFailed=true, the parent terminates when the first sub-execution fails,
        // but other sub-executions continue running their errors/finally tasks in parallel.
        // Wait for all sub-executions to reach terminal state before asserting.
        Await.until(
            () -> executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId()).stream().allMatch(e -> e.getState().isTerminated()),
            Duration.ofMillis(100),
            Duration.ofSeconds(30)
        );
        var subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions.size()).isEqualTo(3);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("e1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("e2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows(value = {"flows/valids/finally-loop-parallel.yaml"}, tenantId = "loopparallelwithouterrors")
    void loopParallelWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "loopparallelwithouterrors",
            NAMESPACE, "finally-loop-parallel", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", false)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        var subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions.size()).isEqualTo(3);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("ok").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows(value = { "flows/valids/finally-loop-parallel.yaml" }, tenantId = "loopparallelwitherrors")
    void loopParallelWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "loopparallelwitherrors",
            NAMESPACE, "finally-loop-parallel", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // With transmitFailed=true, the parent terminates when the first sub-execution fails,
        // but other sub-executions continue running their errors/finally tasks in parallel.
        // Wait for all sub-executions to reach terminal state before asserting.
        Await.until(
            () -> executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId()).stream().allMatch(e -> e.getState().isTerminated()),
            Duration.ofMillis(100),
            Duration.ofSeconds(30)
        );
        var subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions.size()).isEqualTo(3);

        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("e1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(subExecutions.getFirst().findTaskRunsByTaskId("e2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows(value = { "flows/valids/finally-dag.yaml" }, tenantId = "dagwithouterrors")
    void dagWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "dagwithouterrors",
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
    @LoadFlows(value = { "flows/valids/finally-dag.yaml" }, tenantId = "dagwitherrors")
    void dagWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "dagwitherrors",
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
    @LoadFlows(value = { "flows/valids/finally-flow.yaml" }, tenantId = "flowwithouterrors")
    void flowWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "flowwithouterrors",
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
    @LoadFlows(value = { "flows/valids/finally-flow.yaml" }, tenantId = "flowwitherrors")
    void flowWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "flowwitherrors",
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
    @LoadFlows(value = { "flows/valids/finally-flow-error.yaml" }, tenantId = "flowerrorblockwithouterrors")
    void flowErrorBlockWithoutErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "flowerrorblockwithouterrors",
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
    @LoadFlows(value = { "flows/valids/finally-flow-error.yaml" }, tenantId = "flowerrorblockwitherrors")
    void flowErrorBlockWithErrors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            "flowerrorblockwitherrors",
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
    @LoadFlows(value = { "flows/valids/finally-flow-error-first.yaml" }, tenantId = "flowerrorfirst")
    void flowErrorFirst() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne("flowerrorfirst", NAMESPACE, "finally-flow-error-first");

        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ok").isEmpty()).isTrue();
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }
}