package org.kestra.core.tasks.flows;

import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class EachSequentialTest extends AbstractMemoryRunnerTest {
    @Test
    void sequential() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "each-sequential");

        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @Test
    void sequentialNested() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "each-sequential-nested");

        assertThat(execution.getTaskRunList(), hasSize(23));
    }
}
