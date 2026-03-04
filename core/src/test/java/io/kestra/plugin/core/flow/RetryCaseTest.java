package io.kestra.plugin.core.flow;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.utils.Await;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Singleton
public class RetryCaseTest {

    @Inject
    protected TestRunnerUtils runnerUtils;
    @Inject
    private ExecutionRepositoryInterface executionRepository;
    @Inject
    private FlowRepositoryInterface flowRepository;

    public void retrySuccess(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getAttempts()).hasSize(4);
    }

    public void retrySuccessAtFirstAttempt(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getAttempts()).hasSize(1);
    }

    public void retryFailed(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getTaskRunList().getFirst().getAttempts()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void retryRandom(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getAttempts()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void retryExpo(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getAttempts()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void retryFail(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getTaskRunList().getFirst().getAttempts()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

    }

    public void retryNewExecutionTaskDuration(String tenant) throws TimeoutException, QueueException {
        var flow = flowRepository
            .findById(tenant, "io.kestra.tests", "retry-new-execution-task-duration")
            .orElseThrow();
        runAndAssertThereWasTwoRetriesAndFinishedFailed(flow);
    }

    public void retryNewExecutionTaskAttempts(String tenant) throws TimeoutException, QueueException {
        var flow = flowRepository
            .findById(tenant, "io.kestra.tests", "retry-new-execution-task-attempts")
            .orElseThrow();
        runAndAssertThereWasTwoRetriesAndFinishedFailed(flow);
    }

    public void retryNewExecutionFlowDuration(String tenant) throws TimeoutException, QueueException {
        var flow = flowRepository
            .findById(tenant, "io.kestra.tests", "retry-new-execution-flow-duration")
            .orElseThrow();
        runAndAssertThereWasTwoRetriesAndFinishedFailed(flow);
    }

    public void retryNewExecutionFlowAttempts(String tenant) throws TimeoutException, QueueException {
        var flow = flowRepository
            .findById(tenant, "io.kestra.tests", "retry-new-execution-flow-attempts")
            .orElseThrow();
        runAndAssertThereWasTwoRetriesAndFinishedFailed(flow);
    }

    private void runAndAssertThereWasTwoRetriesAndFinishedFailed(Flow flow) throws TimeoutException, QueueException {
        runnerUtils.runOne(
            Execution.newExecution(flow, null),
            flow,
            Duration.ofSeconds(10)
        );
        Await.until(
            () -> "flow should have ended in Failed state",
            () -> executionRepository.findLatestForStates(flow.getTenantId(), flow.getNamespace(), flow.getId(), List.of(State.Type.FAILED)).isPresent(),
            Duration.ofMillis(100),
            Duration.ofSeconds(10)
        );
        var executions = executionRepository.findByFlowId(flow.getTenantId(), flow.getNamespace(), flow.getId(), Pageable.UNPAGED);
        assertThat(executions.stream().map(e -> e.getState().getCurrent())).contains(State.Type.RETRIED, State.Type.RETRIED, State.Type.FAILED);
    }

    public void retryFailedTaskDuration(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getFirst().attemptNumber()).isGreaterThanOrEqualTo(2);
    }

    public void retryFailedTaskAttempts(Execution execution) {

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getFirst().attemptNumber()).isEqualTo(4);
    }

    public void retryFailedFlowDuration(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getFirst().attemptNumber()).isEqualTo(3);
    }

    public void retryFailedFlowAttempts(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getFirst().attemptNumber()).isEqualTo(4);
    }

    public void retryFlowable(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().get(1).attemptNumber()).isEqualTo(3);
    }

    public void retrySubflow(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().get(0).getAttempts().size()).isEqualTo(3);
    }

    public void retryFlowableChild(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().get(1).attemptNumber()).isEqualTo(3);
    }

    public void retryFlowableNestedChild(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().get(2).attemptNumber()).isEqualTo(3);
    }

    public void retryFlowableParallel(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().get(1).attemptNumber()).isGreaterThanOrEqualTo(2);
        assertThat(execution.getTaskRunList().get(2).attemptNumber()).isGreaterThanOrEqualTo(2);
    }

    public void retryDynamicTask(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void retryWithFlowableErrors(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getTaskRunList().get(2).getAttempts()).satisfiesExactly(
            attempt1 -> assertThat(attempt1.getState().getCurrent()).isEqualTo(State.Type.FAILED),
            attempt2 -> assertThat(attempt2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS)
        );
        assertThat(execution.getTaskRunList().get(2).attemptNumber()).isEqualTo(2);
    }
}
