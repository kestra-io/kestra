package org.kestra.core.runners;

import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class DisabledTest extends AbstractMemoryRunnerTest {
    @Test
    void simple() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "disable-simple");

        assertThat(execution.getTaskRunList(), hasSize(2));
    }

    @Test
    void error() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "disable-error");

        assertThat(execution.getTaskRunList(), hasSize(3));
    }

    @Test
    void flowable() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "disable-flowable");

        assertThat(execution.getTaskRunList(), hasSize(10));
    }
}
