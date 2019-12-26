package org.kestra.core.tasks.flows;

import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class ParallelTest extends AbstractMemoryRunnerTest {
    @Test
    void parallel() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "parallel");

        assertThat(execution.getTaskRunList(), hasSize(8));
    }
}