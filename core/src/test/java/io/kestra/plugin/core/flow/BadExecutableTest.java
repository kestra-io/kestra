package io.kestra.plugin.core.flow;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class BadExecutableTest extends AbstractMemoryRunnerTest {
    @Test
    void badExecutable() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "executable-fail");

        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}
