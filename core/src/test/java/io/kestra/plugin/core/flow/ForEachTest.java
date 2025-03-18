package io.kestra.plugin.core.flow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
class ForEachTest {

    @Test
    @ExecuteFlow("flows/valids/foreach-non-concurrent.yaml")
    void nonConcurrent(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(7));
    }

    @Test
    @ExecuteFlow("flows/valids/foreach-concurrent.yaml")
    void concurrent(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(7));
    }

    @Test
    @ExecuteFlow("flows/valids/foreach-concurrent-parallel.yaml")
    void concurrentWithParallel(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(10));
    }

    @Test
    @ExecuteFlow("flows/valids/foreach-concurrent-no-limit.yaml")
    void concurrentNoLimit(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(7));
    }

    @Test
    @ExecuteFlow("flows/valids/foreach-disabled-tasks.yaml")
    void disabledTasks(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.getTaskRunList(), hasSize(1));
    }

    @Test
    @ExecuteFlow("flows/valids/foreach-error.yaml")
    void errors(Execution execution) {
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList(), hasSize(6));
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent(), is(State.Type.SUCCESS));
    }
}