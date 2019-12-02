package org.floworc.core.tasks.flows;

import org.floworc.core.runners.AbstractMemoryRunnerTest;
import org.floworc.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class ParallelTest extends AbstractMemoryRunnerTest {
    @Test
    void parallel() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.floworc.tests", "parallel");

        assertThat(execution.getTaskRunList(), hasSize(8));
    }
}