package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AliasTest extends AbstractMemoryRunnerTest {
    @Test
    void taskAlias() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "alias-task");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList().size(), is(2));
    }

    @Test
    void triggerAlias() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "alias-trigger");

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList().size(), is(1));
    }
}
