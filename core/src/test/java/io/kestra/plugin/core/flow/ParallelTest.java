package io.kestra.plugin.core.flow;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.TestRunnerUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@KestraTest(startRunner = true)
class ParallelTest {
    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    private FlowInputOutput flowIO;

    @Test
    @ExecuteFlow("flows/valids/parallel.yaml")
    void parallel(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(8);
    }

    @Test
    @ExecuteFlow("flows/valids/parallel-nested.yaml")
    void parallelNested(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(11);
    }

    @Test
    @LoadFlows({"flows/valids/finally-parallel.yaml"})
    void errors() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests", "finally-parallel", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("failed", true)),
            Duration.ofSeconds(60)
        );

        assertThat(execution.getTaskRunList()).hasSize(10);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("ko").getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("a1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e1").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getStartDate().isAfter(execution.findTaskRunsByTaskId("a1").getFirst().getState().getEndDate().orElseThrow())).isTrue();
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getStartDate().isAfter(execution.findTaskRunsByTaskId("e1").getFirst().getState().getEndDate().orElseThrow())).isTrue();
    }

    @Test
    @ExecuteFlow("flows/valids/parallel-fail-with-flowable.yaml")
    void parallelFailWithFlowable(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList()).hasSize(5);
        // all tasks must be terminated except the Sleep that will ends later as everything is concurrent
        execution.getTaskRunList().stream()
            .filter(taskRun -> !"sleep".equals(taskRun.getTaskId()))
            .forEach(run -> assertThat(run.getState().isTerminated()).isTrue());
    }
}
