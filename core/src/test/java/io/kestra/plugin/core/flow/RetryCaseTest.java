package io.kestra.plugin.core.flow;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

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

    public void retryNewExecutionSubflowOutputs(String tenant) throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(tenant, "io.kestra.tests", "subflow-retry-new-execution-parent", Duration.ofSeconds(30));

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("read").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    private void runAndAssertThereWasTwoRetriesAndFinishedFailed(Flow flow) throws TimeoutException, QueueException {
        runnerUtils.runOne(
            Execution.newExecution(flow, null),
            flow,
            Duration.ofSeconds(30)
        );
        Await.until(
            () -> "flow should have ended in Failed state",
            () ->
            {
                try {
                    return executionRepository.findLatestForStates(flow.getTenantId(), flow.getNamespace(), flow.getId(), List.of(State.Type.FAILED)).isPresent();
                } catch (Exception e) {
                    // Repository may be unavailable during context teardown (e.g., ES connection pool closed)
                    return false;
                }
            },
            Duration.ofMillis(100),
            Duration.ofSeconds(30)
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
        // maxDuration=PT7S with interval=PT2S yields ~3 attempts, but CI load can shift timing
        assertThat(execution.getTaskRunList().getFirst().attemptNumber()).isBetween(2, 4);
    }

    public void retryFailedFlowAttempts(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getFirst().attemptNumber()).isEqualTo(4);
    }

    public void retryFlowable(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().get(1).attemptNumber()).isEqualTo(3);
    }

    public void retrySubflow(String tenant) throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(tenant, "io.kestra.tests", "retry-subflow");
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
        assertThat(execution.getTaskRunList()).hasSize(1);
        // The Loop task run itself is not retried; its child tasks are retried within their sub-executions
        assertThat(execution.getTaskRunList().getFirst().attemptNumber()).isEqualTo(1);

        // At least one sub-execution must have retried its child task
        var subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId(), null);
        assertThat(subExecutions).hasSize(2);
        boolean anyChildRetried = subExecutions.stream()
            .flatMap(sub -> sub.getTaskRunList().stream())
            .anyMatch(tr -> tr.getTaskId().equals("child") && tr.attemptNumber() >= 2);
        assertThat(anyChildRetried).isTrue();
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

    public void retryFlowableBehavior(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        // the flowable is retried as a unit, up to maxAttempts
        assertThat(execution.findTaskRunsByTaskId("seq1").getFirst().getAttempts()).hasSize(3);
        // children are wiped per attempt: only the last attempt's runs remain
        assertThat(execution.findTaskRunsByTaskId("get_token")).hasSize(1);
        assertThat(execution.findTaskRunsByTaskId("call_api")).hasSize(1);
        // a child without its own retry must not be retried as a leaf under a RETRY_FLOWABLE parent
        assertThat(execution.findTaskRunsByTaskId("call_api").getFirst().getAttempts()).hasSize(1);
    }

    public void retryFlowableBehaviorSuccess(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        // fails on attempts 1 and 2, succeeds on attempt 3, and stops retrying
        assertThat(execution.findTaskRunsByTaskId("seq1").getFirst().getAttempts()).hasSize(3);
        assertThat(execution.findTaskRunsByTaskId("check")).hasSize(1);
        assertThat(execution.findTaskRunsByTaskId("check").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void retryFlowableBehaviorAllowFailure(Execution execution) {
        // the failure is only allowed after the last attempt, so the next task runs once
        assertThat(execution.findTaskRunsByTaskId("allow").getFirst().getAttempts()).hasSize(2);
        assertThat(execution.findTaskRunsByTaskId("after")).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
    }

    public void retryFlowableBehaviorNestedLeaf(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        // parent retried as a unit; the leaf keeps its own retry inside each round
        assertThat(execution.findTaskRunsByTaskId("seq1").getFirst().getAttempts()).hasSize(2);
        assertThat(execution.findTaskRunsByTaskId("leaf")).hasSize(1);
        assertThat(execution.findTaskRunsByTaskId("leaf").getFirst().getAttempts()).hasSize(2);
    }
}
