package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class RetryTest extends AbstractMemoryRunnerTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Test
    void retrySuccess() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "retry-success");

        assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts(), hasSize(5));
    }

    @Test
    void retrySuccessAtFirstAttempt() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "retry-success-first-attempt");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts(), hasSize(1));
    }

    @Test
    void retryFailed() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "retry-failed");

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getTaskRunList().get(0).getAttempts(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void retryRandom() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "retry-random");

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void retryExpo() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "retry-expo");

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void retryFail() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "retry-and-fail");

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getTaskRunList().get(0).getAttempts(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

    }
}
