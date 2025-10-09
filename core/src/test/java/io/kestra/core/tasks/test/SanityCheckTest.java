package io.kestra.core.tasks.test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class SanityCheckTest {
    @Test
    @ExecuteFlow("sanity-checks/fail.yaml")
    void qaFail(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("sanity-checks/fetch.yaml")
    void qaFetch(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("sanity-checks/if.yaml")
    void qaIf(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(8);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @FlakyTest
    @Test
    @ExecuteFlow("sanity-checks/kv.yaml")
    void qaKv(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(6);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("sanity-checks/labels.yaml")
    void qaLabels(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("sanity-checks/namespace_files.yaml")
    void qaNamespaceFiles(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(8);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("sanity-checks/parallel.yaml")
    void qaParallel(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(4);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("sanity-checks/pause-test.yaml")
    void qaPause(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("sanity-checks/purge_current_execution_files.yaml")
    void qaPurgeExecutionFiles(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("sanity-checks/return.yaml")
    void qaReturn(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        TaskRun taskRun = execution.findTaskRunsByTaskId("return_value").getFirst();
        assertThat(taskRun.getOutputs().get("value")).isEqualTo("some string with pebble test");
    }

    @Test
    @ExecuteFlow("sanity-checks/sequential.yaml")
    void qaSequential(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("sanity-checks/switch.yaml")
    void qaSwitch(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("sanity-checks/write.yaml")
    void qaWrite(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("sanity-checks/purge_kv.yaml")
    void qaPurgeKv(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(6);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("sanity-checks/output_values.yaml")
    void qaOutputValues(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }
}
