package io.kestra.plugin.core.flow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class BadExecutableTest {

    @Test
    @ExecuteFlow("flows/valids/executable-fail.yml")
    void badExecutable(Execution execution) {
        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
    }
}
