package org.floworc.runner.memory;

import org.floworc.core.AbstractMemoryRunnerTest;
import org.floworc.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class MemoryRunnerTest extends AbstractMemoryRunnerTest {
    @Test
    void full() throws TimeoutException {
        Execution execution = runnerUtils.runOne("full");

        assertThat(execution.getTaskRunList(), hasSize(13));
    }

    @Test
    void errors() throws TimeoutException {
        Execution execution = runnerUtils.runOne("errors");

        assertThat(execution.getTaskRunList(), hasSize(7));
    }
}