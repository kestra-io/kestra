package io.kestra.plugin.core.flow;

import java.time.Duration;
import java.util.List;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.services.TaskOutputService;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class FlowCaseTest {

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    private TaskOutputService taskOutputService;

    public void waitSuccess(String tenantId) throws Exception {
        this.run("OK", State.Type.SUCCESS, State.Type.SUCCESS, 2, true, tenantId);
    }

    public void waitFailed(String tenantId) throws Exception {
        this.run("THIRD", State.Type.FAILED, State.Type.FAILED, 4, true, tenantId);
    }

    public void noLabels(String tenantId) throws Exception {
        this.run("OK", State.Type.SUCCESS, State.Type.SUCCESS, 2, false, tenantId);
    }

    public void oldTaskName(String tenantId) throws Exception {
        Execution execution = runnerUtils.runOne(
            tenantId,
            "io.kestra.tests",
            "subflow-old-task-name"
        );

        Execution triggered = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().isTerminated(), tenantId, "io.kestra.tests",
            "minimal"
        );

        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().getFirst()).get("executionId")).isEqualTo(triggered.getId());
        assertThat(triggered.getTrigger().getType()).isEqualTo("io.kestra.core.tasks.flows.Subflow");
        assertThat(triggered.getTrigger().getVariables().get("executionId")).isEqualTo(execution.getId());
        assertThat(triggered.getTrigger().getVariables().get("flowId")).isEqualTo(execution.getFlowId());
        assertThat(triggered.getTrigger().getVariables().get("namespace")).isEqualTo(execution.getNamespace());
    }

    void run(String input, State.Type fromState, State.Type triggerState, int count, boolean testInherited, String tenantId) throws Exception {
        Execution execution = runnerUtils.runOne(
            tenantId,
            "io.kestra.tests",
            testInherited ? "task-flow" : "task-flow-inherited-labels",
            null,
            (f, e) -> ImmutableMap.of("string", input),
            Duration.ofMinutes(1),
            testInherited ? List.of(new Label("mainFlowExecutionLabel", "execFoo")) : List.of()
        );

        Execution triggered = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().isTerminated(), tenantId, "io.kestra.tests", "switch"
        );

        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getAttempts()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getAttempts().getFirst().getState().getCurrent()).isEqualTo(fromState);
        assertThat(execution.getState().getCurrent()).isEqualTo(fromState);

        assertThat(taskOutputService.getOutputs(execution.getTaskRunList().getFirst()).get("executionId")).isEqualTo(triggered.getId());

        assertThat(triggered.getTrigger().getType()).isEqualTo("io.kestra.plugin.core.flow.Subflow");
        assertThat(triggered.getTrigger().getVariables().get("executionId")).isEqualTo(execution.getId());
        assertThat(triggered.getTrigger().getVariables().get("flowId")).isEqualTo(execution.getFlowId());
        assertThat(triggered.getTrigger().getVariables().get("namespace")).isEqualTo(execution.getNamespace());

        assertThat(triggered.getTaskRunList()).hasSize(count);
        assertThat(triggered.getState().getCurrent()).isEqualTo(triggerState);

        if (testInherited) {
            assertThat(triggered.getLabels().size()).isEqualTo(6);
            assertThat(triggered.getLabels()).contains(
                new Label(Label.CORRELATION_ID, execution.getId()), new Label("mainFlowExecutionLabel", "execFoo"), new Label("mainFlowLabel", "flowFoo"),
                new Label("launchTaskLabel", "launchFoo"), new Label("switchFlowLabel", "switchFoo"), new Label("overriding", "child")
            );
        } else {
            assertThat(triggered.getLabels().size()).isEqualTo(4);
            assertThat(triggered.getLabels()).contains(
                new Label(Label.CORRELATION_ID, execution.getId()), new Label("launchTaskLabel", "launchFoo"), new Label("switchFlowLabel", "switchFoo"), new Label("overriding", "child")
            );
            assertThat(triggered.getLabels()).doesNotContain(new Label("inherited", "label"));
        }
    }
}
