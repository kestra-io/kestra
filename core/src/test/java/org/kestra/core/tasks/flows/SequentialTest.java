package org.kestra.core.tasks.flows;

import org.kestra.core.models.flows.State;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class SequentialTest extends AbstractMemoryRunnerTest {
    @Test
    void sequential() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "sequential");

        assertThat(execution.getTaskRunList(), hasSize(11));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void sequentialWithGlobalErrors() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "sequential-with-global-errors");

        assertThat(execution.getTaskRunList(), hasSize(6));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void sequentialWithLocalErrors() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "sequential-with-local-errors");

        assertThat(execution.getTaskRunList(), hasSize(6));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}