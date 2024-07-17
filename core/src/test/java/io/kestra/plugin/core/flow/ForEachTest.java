package io.kestra.plugin.core.flow;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class ForEachTest  extends AbstractMemoryRunnerTest {
    @Test
    void nonConcurrent() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "foreach-non-concurrent");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(7));
    }

    @Test
    void concurrent() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "foreach-concurrent");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(7));
    }

    @Test
    void concurrentWithParallel() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "foreach-concurrent-parallel");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(10));
    }
}