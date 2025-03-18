package io.kestra.plugin.core.flow;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Slf4j
@Singleton
public class RetryCaseTest {

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    protected RunnerUtils runnerUtils;

    public void retrySuccess(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getAttempts(), hasSize(4));
    }

    public void retrySuccessAtFirstAttempt(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getAttempts(), hasSize(1));
    }

    public void retryFailed(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getTaskRunList().getFirst().getAttempts(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    public void retryRandom(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getAttempts(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    public void retryExpo(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getAttempts(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    public void retryFail(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getTaskRunList().getFirst().getAttempts(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

    }

    public void retryNewExecutionTaskDuration() throws TimeoutException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicReference<List<State.Type>> stateHistory = new AtomicReference<>(new ArrayList<>());

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("retry-new-execution-task-duration") && execution.getState().getCurrent().isTerminated()) {
                List<State.Type> stateHistoryList = stateHistory.get();
                stateHistoryList.add(execution.getState().getCurrent());
                stateHistory.set(stateHistoryList);
                countDownLatch.countDown();
            }
        });

        runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "retry-new-execution-task-duration",
            null,
            null
        );

        Await.until(() -> countDownLatch.getCount() == 0, Duration.ofSeconds(2), Duration.ofMinutes(1));
        receive.blockLast();
        assertThat(stateHistory.get(), containsInAnyOrder(State.Type.RETRIED, State.Type.RETRIED, State.Type.FAILED));
    }

    public void retryNewExecutionTaskAttempts() throws TimeoutException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicReference<List<State.Type>> stateHistory = new AtomicReference<>(new ArrayList<>());

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("retry-new-execution-task-attempts") && execution.getState().getCurrent().isTerminated()) {
                List<State.Type> stateHistoryList = stateHistory.get();
                stateHistoryList.add(execution.getState().getCurrent());
                stateHistory.set(stateHistoryList);
                countDownLatch.countDown();
            }
        });

        runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "retry-new-execution-task-attempts",
            null,
            null
        );

        Await.until(() -> countDownLatch.getCount() == 0, Duration.ofSeconds(2), Duration.ofMinutes(1));
        receive.blockLast();
        assertThat(stateHistory.get(), containsInAnyOrder(State.Type.RETRIED, State.Type.RETRIED, State.Type.FAILED));
    }

    public void retryNewExecutionFlowDuration() throws TimeoutException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicReference<List<State.Type>> stateHistory = new AtomicReference<>(new ArrayList<>());

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("retry-new-execution-flow-duration") && execution.getState().getCurrent().isTerminated()) {
                List<State.Type> stateHistoryList = stateHistory.get();
                stateHistoryList.add(execution.getState().getCurrent());
                stateHistory.set(stateHistoryList);
                countDownLatch.countDown();
            }
        });

        runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "retry-new-execution-flow-duration",
            null,
            null
        );

        Await.until(() -> countDownLatch.getCount() == 0, Duration.ofSeconds(2), Duration.ofMinutes(1));
        receive.blockLast();
        assertThat(stateHistory.get(), containsInAnyOrder(State.Type.RETRIED, State.Type.RETRIED, State.Type.FAILED));
    }

    public void retryNewExecutionFlowAttempts() throws TimeoutException, QueueException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicReference<List<State.Type>> stateHistory = new AtomicReference<>(new ArrayList<>());

        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("retry-new-execution-flow-attempts") && execution.getState().getCurrent().isTerminated()) {
                List<State.Type> stateHistoryList = stateHistory.get();
                stateHistoryList.add(execution.getState().getCurrent());
                stateHistory.set(stateHistoryList);
                countDownLatch.countDown();
            }
        });

        runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "retry-new-execution-flow-attempts",
            null,
            null
        );

        Await.until(() -> countDownLatch.getCount() == 0, Duration.ofSeconds(2), Duration.ofMinutes(1));
        receive.blockLast();
        assertThat(stateHistory.get(), containsInAnyOrder(State.Type.RETRIED, State.Type.RETRIED, State.Type.FAILED));
    }

    public void retryFailedTaskDuration(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().getFirst().attemptNumber(), is(3));
    }

    public void retryFailedTaskAttempts(Execution execution) {

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().getFirst().attemptNumber(), is(4));
    }

    public void retryFailedFlowDuration(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().getFirst().attemptNumber(), is(3));
    }

    public void retryFailedFlowAttempts(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().getFirst().attemptNumber(), is(4));
    }

    public void retryFlowable(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().get(1).attemptNumber(), is(3));
    }

    public void retrySubflow(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().get(0).getAttempts().size(), is(3));
    }

    public void retryFlowableChild(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().get(1).attemptNumber(), is(3));
    }

    public void retryFlowableNestedChild(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().get(2).attemptNumber(), is(3));
    }

    public void retryFlowableParallel(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().get(1).attemptNumber(), greaterThanOrEqualTo(2));
        assertThat(execution.getTaskRunList().get(2).attemptNumber(), greaterThanOrEqualTo(2));
    }

    public void retryDynamicTask(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

}
