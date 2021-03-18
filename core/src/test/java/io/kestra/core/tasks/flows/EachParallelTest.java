package io.kestra.core.tasks.flows;

import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.AbstractMemoryRunnerTest;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class EachParallelTest extends AbstractMemoryRunnerTest {
    @Test
    void parallel() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "each-parallel");

        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @Test
    void parallelNested() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "each-parallel-nested");

        assertThat(execution.getTaskRunList(), hasSize(11));
    }
}
