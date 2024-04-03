package io.kestra.core.tasks.flows;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.utils.Await;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

@Slf4j
@Singleton
public class RetryCaseTest {

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    protected RunnerUtils runnerUtils;

    public void retryNewExecutionTaskDuration() throws TimeoutException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicReference<List<State.Type>> stateHistory = new AtomicReference<>(new ArrayList<>());

        executionQueue.receive(either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("retry-new-execution-task-duration") && execution.getState().getCurrent().isTerminated()) {
                countDownLatch.countDown();
                List<State.Type> stateHistoryList = stateHistory.get();
                stateHistoryList.add(execution.getState().getCurrent());
                stateHistory.set(stateHistoryList);
            }
        });

        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "retry-new-execution-task-duration",
            null,
            null
        );

        Await.until(() -> countDownLatch.getCount() == 0, Duration.ofSeconds(2), Duration.ofMinutes(1));
        assertThat(stateHistory.get(), containsInAnyOrder(State.Type.RETRIED, State.Type.RETRIED, State.Type.FAILED));
    }

    public void retryNewExecutionTaskAttempts() throws TimeoutException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicReference<List<State.Type>> stateHistory = new AtomicReference<>(new ArrayList<>());

        executionQueue.receive(either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("retry-new-execution-task-attempts") && execution.getState().getCurrent().isTerminated()) {
                countDownLatch.countDown();
                List<State.Type> stateHistoryList = stateHistory.get();
                stateHistoryList.add(execution.getState().getCurrent());
                stateHistory.set(stateHistoryList);
            }
        });

        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "retry-new-execution-task-attempts",
            null,
            null
        );

        Await.until(() -> countDownLatch.getCount() == 0, Duration.ofSeconds(2), Duration.ofMinutes(1));
        assertThat(stateHistory.get(), containsInAnyOrder(State.Type.RETRIED, State.Type.RETRIED, State.Type.FAILED));
    }

    public void retryNewExecutionFlowDuration() throws TimeoutException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicReference<List<State.Type>> stateHistory = new AtomicReference<>(new ArrayList<>());

        executionQueue.receive(either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("retry-new-execution-flow-duration") && execution.getState().getCurrent().isTerminated()) {
                countDownLatch.countDown();
                List<State.Type> stateHistoryList = stateHistory.get();
                stateHistoryList.add(execution.getState().getCurrent());
                stateHistory.set(stateHistoryList);
            }
        });

        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "retry-new-execution-flow-duration",
            null,
            null
        );

        Await.until(() -> countDownLatch.getCount() == 0, Duration.ofSeconds(2), Duration.ofMinutes(1));
        assertThat(stateHistory.get(), containsInAnyOrder(State.Type.RETRIED, State.Type.RETRIED, State.Type.FAILED));
    }

    public void retryNewExecutionFlowAttempts() throws TimeoutException {
        CountDownLatch countDownLatch = new CountDownLatch(3);
        AtomicReference<List<State.Type>> stateHistory = new AtomicReference<>(new ArrayList<>());

        executionQueue.receive(either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("retry-new-execution-flow-attempts") && execution.getState().getCurrent().isTerminated()) {
                countDownLatch.countDown();
                List<State.Type> stateHistoryList = stateHistory.get();
                stateHistoryList.add(execution.getState().getCurrent());
                stateHistory.set(stateHistoryList);
            }
        });

        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "retry-new-execution-flow-attempts",
            null,
            null
        );

        Await.until(() -> countDownLatch.getCount() == 0, Duration.ofSeconds(2), Duration.ofMinutes(1));
        assertThat(stateHistory.get(), containsInAnyOrder(State.Type.RETRIED, State.Type.RETRIED, State.Type.FAILED));
    }

    public void retryFailedTaskDuration() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "retry-failed-task-duration",
            null,
            null
        );

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().get(0).attemptNumber(), is(3));
    }

    public void retryFailedTaskAttempts() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "retry-failed-task-attempts",
            null,
            null,
            Duration.ofSeconds(20)
        );

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().get(0).attemptNumber(), is(4));
    }

    public void retryFailedFlowDuration() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "retry-failed-flow-duration",
            null,
            null
        );

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().get(0).attemptNumber(), is(3));
    }

    public void retryFailedFlowAttempts() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "retry-failed-flow-attempts",
            null,
            null
        );

        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList().get(0).attemptNumber(), is(4));
    }

}
