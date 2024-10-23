package io.kestra.plugin.core.flow;

import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class SequentialTest extends AbstractMemoryRunnerTest {
    @Test
    void sequential() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "sequential");

        assertThat(execution.getTaskRunList(), hasSize(11));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void waitTaskTest() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "wait-task-flow");

        // Assert that the execution state is SUCCESS after the wait task completes
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }


    @Test
    void sequentialWithGlobalErrors() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "sequential-with-global-errors");

        assertThat(execution.getTaskRunList(), hasSize(6));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void sequentialWithLocalErrors() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "sequential-with-local-errors");

        assertThat(execution.getTaskRunList(), hasSize(6));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}