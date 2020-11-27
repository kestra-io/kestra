package org.kestra.core.tasks.flows;

import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.runners.AbstractMemoryRunnerTest;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class AllowFailureTest extends AbstractMemoryRunnerTest {
    @Test
    void sequential() throws TimeoutException {
        Execution execution = runnerUtils.runOne("org.kestra.tests", "allow-failure");

        assertThat(execution.getTaskRunList(), hasSize(7));
        assertThat(execution.findTaskRunsByTaskId("1").get(0).getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.findTaskRunsByTaskId("1-1-allow-failure").get(0).getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.findTaskRunsByTaskId("1-1-1_seq").get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("1-1-1-1").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("ko").get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.findTaskRunsByTaskId("error-t2").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("error-t1").get(0).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
    }
}