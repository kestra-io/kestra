package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.TestRunnerUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class IfTest {

    private static final String TENANT_ID = "true";

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows(value = {"flows/valids/if-condition.yaml"}, tenantId = TENANT_ID)
    void ifTruthy() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(TENANT_ID, "io.kestra.tests", "if-condition", null,
            (f, e) -> Map.of("param", true) , Duration.ofSeconds(120));
        List<TaskRunAttempt> flowableAttempts=execution.findTaskRunsByTaskId("if").getFirst().getAttempts();

        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.findTaskRunsByTaskId("when-true").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertThat(flowableAttempts).isNotNull();
        assertThat(flowableAttempts.getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        execution = runnerUtils.runOne(TENANT_ID, "io.kestra.tests", "if-condition", null,
            (f, e) -> Map.of("param", "true") , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.findTaskRunsByTaskId("when-true").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        execution = runnerUtils.runOne(TENANT_ID, "io.kestra.tests", "if-condition", null,
            (f, e) -> Map.of("param", 1) , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.findTaskRunsByTaskId("when-true").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/if-condition.yaml"})
    void ifFalsy() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "if-condition", null,
            (f, e) -> Map.of("param", false) , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.findTaskRunsByTaskId("when-false").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "if-condition", null,
            (f, e) -> Map.of("param", "false") , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.findTaskRunsByTaskId("when-false").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "if-condition", null,
            (f, e) -> Map.of("param", 0) , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.findTaskRunsByTaskId("when-false").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "if-condition", null,
            (f, e) -> Map.of("param", -0) , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.findTaskRunsByTaskId("when-false").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // We cannot test null as inputs cannot be null
    }

    @Test
    @LoadFlows({"flows/valids/if-without-else.yaml"})
    void ifWithoutElse() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "if-without-else", null,
            (f, e) -> Map.of("param", true) , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.findTaskRunsByTaskId("when-true").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "if-without-else", null,
            (f, e) -> Map.of("param", false) , Duration.ofSeconds(120));
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.findTaskRunsByTaskId("when-true").isEmpty()).isTrue();
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/if-in-flowable.yaml"})
    void ifInFlowable() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "if-in-flowable", null,
            (f, e) -> Map.of("param", true) , Duration.ofSeconds(120));

        assertThat(execution.getTaskRunList()).hasSize(8);
        assertThat(execution.findTaskRunsByTaskId("after_if").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("flows/valids/if-with-only-disabled-tasks.yaml")
    void ifWithOnlyDisabledTasks(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.findTaskRunsByTaskId("if").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("flows/valids/if-in-parallel.yaml")
    void ifOnParallelBranch(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(9);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @ExecuteFlow("flows/valids/if-condition-fail.yaml")
    void ifConditionFail(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList().getFirst().getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }
}