package io.kestra.core.runners;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class TaskWithRunIfTest {

    @Test
    @ExecuteFlow("flows/valids/task-runif.yml")
    void runnableTask(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.findTaskRunsByTaskId("executed").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("notexecuted").getFirst().getState().getCurrent(), is(State.Type.SKIPPED));
        assertThat(execution.findTaskRunsByTaskId("willfailedtheflow").getFirst().getState().getCurrent(), is(State.Type.FAILED));
    }

}
