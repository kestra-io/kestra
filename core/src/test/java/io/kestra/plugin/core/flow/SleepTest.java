package io.kestra.plugin.core.flow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class SleepTest {

    @Test
    @ExecuteFlow("flows/valids/sleep-task-flow.yaml")
    void sleepTaskTest(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }


}
