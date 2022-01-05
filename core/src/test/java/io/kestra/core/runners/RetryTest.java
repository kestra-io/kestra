package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class RetryTest extends AbstractMemoryRunnerTest {
    @Test
    void retrySuccess() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "retry-success");

        assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts(), hasSize(5));
    }

    @Test
    void retryFailed() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "retry-failed");

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getTaskRunList().get(0).getAttempts(), hasSize(5));
    }
}
