package io.kestra.core.tasks.flows;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.services.ExecutionService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;

import java.time.Duration;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class PauseTest extends AbstractMemoryRunnerTest {
    @Inject
    Suite suite;

    @Test
    void run() throws Exception {
        suite.run(runnerUtils);
    }

    @RetryingTest(maxAttempts = 10, suspendForMs = 10)
    void delay() throws Exception {
        suite.runDelay(runnerUtils);
    }

    @Test
    void timeout() throws Exception {
        suite.runTimeout(runnerUtils);
    }

    @Singleton
    public static class Suite {
        @Inject
        ExecutionService executionService;

        @Inject
        FlowRepositoryInterface flowRepository;

        @Inject
        @Named(QueueFactoryInterface.EXECUTION_NAMED)
        protected QueueInterface<Execution> executionQueue;

        public void run(RunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused("io.kestra.tests", "pause");

            assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));
            assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.PAUSED));
            assertThat(execution.getTaskRunList(), hasSize(1));

            Execution restarted = executionService.markAs(
                execution,
                execution.findTaskRunByTaskIdAndValue("pause", List.of()).getId(),
                State.Type.RUNNING
            );

            execution = runnerUtils.awaitExecution(
                e -> !e.getFlowId().equals("trigger-flow-listener-no-condition") && e.getState().getCurrent() == State.Type.SUCCESS,
                () -> executionQueue.emit(restarted),
                Duration.ofSeconds(5)
            );

            assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        }

        public void runDelay(RunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused("io.kestra.tests", "pause-delay");

            assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));
            assertThat(execution.getTaskRunList(), hasSize(1));

            execution = runnerUtils.awaitExecution(
                e -> !e.getFlowId().equals("trigger-flow-listener-no-condition") && e.getState().getCurrent() == State.Type.SUCCESS,
                () -> {},
                Duration.ofSeconds(5)
            );

            assertThat(execution.getTaskRunList().get(0).getState().getHistories().stream().filter(history -> history.getState() == State.Type.PAUSED).count(), is(1L));
            assertThat(execution.getTaskRunList().get(0).getState().getHistories().stream().filter(history -> history.getState() == State.Type.RUNNING).count(), is(2L));
            assertThat(execution.getTaskRunList(), hasSize(3));
        }


        public void runTimeout(RunnerUtils runnerUtils) throws Exception {
            Execution execution = runnerUtils.runOneUntilPaused("io.kestra.tests", "pause-timeout");

            assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));
            assertThat(execution.getTaskRunList(), hasSize(1));

            execution = runnerUtils.awaitExecution(
                e -> e.getState().getCurrent() == State.Type.FAILED,
                () -> {},
                Duration.ofSeconds(5)
            );

            assertThat("Task runs were: " + execution.getTaskRunList().toString(), execution.getTaskRunList().get(0).getState().getHistories().stream().filter(history -> history.getState() == State.Type.PAUSED).count(), is(1L));
            assertThat(execution.getTaskRunList().get(0).getState().getHistories().stream().filter(history -> history.getState() == State.Type.RUNNING).count(), is(1L));
            assertThat(execution.getTaskRunList().get(0).getState().getHistories().stream().filter(history -> history.getState() == State.Type.FAILED).count(), is(1L));
            assertThat(execution.getTaskRunList(), hasSize(1));
        }
    }
}