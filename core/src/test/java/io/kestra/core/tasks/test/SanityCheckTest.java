package io.kestra.core.tasks.test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@KestraTest(startRunner = true)
class SanityCheckTest {
    @Test
    @ExecuteFlow("sanity-checks/qa.fail.yaml")
    void qaFail(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("sanity-checks/qa.fetch.yaml")
    void qaFetch(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("sanity-checks/qa.if.yaml")
    void qaIf(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(8));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("sanity-checks/qa.kv.yaml")
    void qaKv(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(6));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("sanity-checks/qa.labels.yaml")
    void qaLabels(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("sanity-checks/qa.namespace_files.yaml")
    void qaNamespaceFiles(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(8));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("sanity-checks/qa.parallel.yaml")
    void qaParallel(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("sanity-checks/qa.pause.yaml")
    void qaPause(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("sanity-checks/qa.purge_current_execution_files.yaml")
    void qaPurgeExecutionFiles(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("sanity-checks/qa.return.yaml")
    void qaReturn(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        TaskRun taskRun = execution.findTaskRunsByTaskId("return_value").getFirst();
        assertThat(taskRun.getOutputs().get("value"), is("some string with pebble test"));
    }

    @Test
    @ExecuteFlow("sanity-checks/qa.sequential.yaml")
    void qaSequential(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("sanity-checks/qa.sleep.yaml")
    void qaSleep(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(4));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("sanity-checks/qa.switch.yaml")
    void qaSwitch(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    @ExecuteFlow("sanity-checks/qa.write.yaml")
    void qaWrite(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
    }
}
