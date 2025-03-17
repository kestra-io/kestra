package io.kestra.core.runners;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@KestraTest(startRunner = true)
class TaskWithRunIfTest {

    @Test
    @ExecuteFlow("flows/valids/task-runif.yml")
    void runnableTask(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.findTaskRunsByTaskId("executed").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("notexecuted").getFirst().getState().getCurrent(), is(State.Type.SKIPPED));
        assertThat(execution.findTaskRunsByTaskId("notexecutedflowable").getFirst().getState().getCurrent(), is(State.Type.SKIPPED));
        assertThat(execution.findTaskRunsByTaskId("willfailedtheflow").getFirst().getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    @ExecuteFlow("flows/valids/task-runif-workingdirectory.yml")
    void runIfWorkingDirectory(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.findTaskRunsByTaskId("log_orders").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("log_test").getFirst().getState().getCurrent(), is(State.Type.SKIPPED));
    }

}
