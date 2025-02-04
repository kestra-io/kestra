package io.kestra.plugin.core.execution;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startRunner = true)
class ExitTest {
    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Test
    @ExecuteFlow("flows/valids/exit.yaml")
    void shouldExitTheExecution(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.getTaskRunList().size(), is(2));
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent(), is(State.Type.WARNING));
    }

    @Test
    @LoadFlows("flows/valids/exit-killed.yaml")
    void shouldExitAndKillTheExecution() throws TimeoutException, QueueException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(3);// We need to wait for 3 execution modifications to be sure all tasks are passed to KILLED
        AtomicReference<Execution> killedExecution = new AtomicReference<>();
        Flux<Execution> receive = TestsUtils.receive(executionQueue, either -> {
            Execution execution = either.getLeft();
            if (execution.getFlowId().equals("exit-killed") && execution.getState().getCurrent().isKilled()) {
                killedExecution.set(execution);
                countDownLatch.countDown();
            }
        });

       runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "exit-killed", null, null, Duration.ofSeconds(30));

        assertTrue(countDownLatch.await(1, TimeUnit.MINUTES));
        receive.blockLast();
        assertThat(killedExecution.get(), notNullValue());
        assertThat(killedExecution.get().getState().getCurrent(), is(State.Type.KILLED));
        assertThat(killedExecution.get().getTaskRunList().size(), is(2));
        assertThat(killedExecution.get().getTaskRunList().getFirst().getState().getCurrent(), is(State.Type.KILLED));
        assertThat(killedExecution.get().getTaskRunList().get(1).getState().getCurrent(), is(State.Type.KILLED));
    }
}