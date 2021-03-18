package io.kestra.runner.memory;

import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class MemoryRunnerTest extends AbstractMemoryRunnerTest {
    @Test
    void full() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "full");

        assertThat(execution.getTaskRunList(), hasSize(13));
    }

    @Test
    void errors() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "errors");

        assertThat(execution.getTaskRunList(), hasSize(7));
    }
}