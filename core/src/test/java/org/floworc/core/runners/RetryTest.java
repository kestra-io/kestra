package org.floworc.core.runners;

import org.floworc.core.AbstractMemoryRunnerTest;
import org.floworc.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class RetryTest extends AbstractMemoryRunnerTest {
    @Test
    void retrySuccess() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.floworc.tests", "retry-success");

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().get(0).getAttempts(), hasSize(5));
    }

    @Test
    void retryFailed() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.floworc.tests", "retry-failed");

        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getTaskRunList().get(0).getAttempts(), hasSize(5));
    }
}
