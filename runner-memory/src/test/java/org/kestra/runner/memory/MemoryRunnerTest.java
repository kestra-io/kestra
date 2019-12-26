package org.kestra.runner.memory;

import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class MemoryRunnerTest extends AbstractMemoryRunnerTest {
    @Test
    void full() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "full");

        assertThat(execution.getTaskRunList(), hasSize(13));
    }

    @Test
    void errors() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "errors");

        assertThat(execution.getTaskRunList(), hasSize(7));
    }
}