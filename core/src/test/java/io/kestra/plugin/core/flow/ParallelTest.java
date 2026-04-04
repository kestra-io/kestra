package io.kestra.plugin.core.flow;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.TestRunnerUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

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
    @LoadFlows({ "flows/valids/finally-parallel.yaml" })
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
        assertThat(execution.findTaskRunsByTaskId("a2").getFirst().getState().getStartDate().isAfter(execution.findTaskRunsByTaskId("a1").getFirst().getState().getEndDate().orElseThrow()))
            .isTrue();
        assertThat(execution.findTaskRunsByTaskId("e2").getFirst().getState().getStartDate().isAfter(execution.findTaskRunsByTaskId("e1").getFirst().getState().getEndDate().orElseThrow()))
            .isTrue();
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

    @Test
    @ExecuteFlow("flows/valids/parallel-disabled-tasks.yaml")
    void parallelDisabledTasks(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(7);
    }

    @Test
    @ExecuteFlow("flows/valids/parallel-failfast.yaml")
    void parallelFailFast(Execution execution) {
        // Given failFast=true and one task fails immediately
        // When the execution completes
        // Then the execution should be FAILED
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // And the fail task should be FAILED
        assertThat(execution.findTaskRunsByTaskId("fail").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.FAILED);

        // And the slow siblings should be KILLED (not allowed to complete)
        assertThat(execution.findTaskRunsByTaskId("slow1").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.KILLED);
        assertThat(execution.findTaskRunsByTaskId("slow2").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.KILLED);
    }

    @Test
    @ExecuteFlow("flows/valids/parallel-failfast-with-errors.yaml")
    void parallelFailFastWithErrors(Execution execution) {
        // Given failFast=true with errors and finally handlers
        // When the execution completes
        // Then the execution should be FAILED
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // And the slow sibling should be KILLED
        assertThat(execution.findTaskRunsByTaskId("slow").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.KILLED);

        // And the error handler should have executed
        assertThat(execution.findTaskRunsByTaskId("error-handler")).isNotEmpty();
        assertThat(execution.findTaskRunsByTaskId("error-handler").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.SUCCESS);

        // And the finally handler should have executed
        assertThat(execution.findTaskRunsByTaskId("finally-handler")).isNotEmpty();
        assertThat(execution.findTaskRunsByTaskId("finally-handler").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("flows/valids/parallel-failfast-allow-failure.yaml")
    void parallelFailFastAllowFailure(Execution execution) {
        // Given failFast=true but the failing task has allowFailure=true
        // When the execution completes
        // Then the execution should be WARNING (not FAILED) since allowFailure downgrades
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);

        // And the normal sibling should have run (not killed)
        assertThat(execution.findTaskRunsByTaskId("normal")).isNotEmpty();
        assertThat(execution.findTaskRunsByTaskId("normal").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("flows/valids/parallel-failfast-disabled.yaml")
    void parallelStop(Execution execution) {
        // Given childFailurePolicy=STOP
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // And all sibling tasks should have completed (not killed)
        assertThat(execution.findTaskRunsByTaskId("log1").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("log2").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("flows/valids/parallel-continue.yaml")
    void parallelContinue(Execution execution) {
        // Given childFailurePolicy=CONTINUE, all siblings run despite a failure
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        assertThat(execution.findTaskRunsByTaskId("fail").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.FAILED);
        assertThat(execution.findTaskRunsByTaskId("log1").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.SUCCESS);
        assertThat(execution.findTaskRunsByTaskId("log2").getFirst().getState().getCurrent())
            .isEqualTo(State.Type.SUCCESS);
    }
}
