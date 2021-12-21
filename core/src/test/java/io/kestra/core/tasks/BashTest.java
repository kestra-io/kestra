package io.kestra.core.tasks;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class BashTest extends AbstractMemoryRunnerTest {
    @Test
    void error() throws TimeoutException {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "bash-warning");

        assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.getTaskRunList().get(0).getOutputs().get("stdErrLineCount"), is(1));
        assertThat(execution.getTaskRunList(), hasSize(1));
    }
}
