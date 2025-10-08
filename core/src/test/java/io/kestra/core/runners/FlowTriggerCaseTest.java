package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.State.Type;
import io.kestra.core.queues.QueueException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class FlowTriggerCaseTest {

    public static final String NAMESPACE = "io.kestra.tests.trigger";

    @Inject
    protected TestRunnerUtils runnerUtils;

    public void trigger(String tenantId) throws InterruptedException, TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(tenantId, NAMESPACE, "trigger-flow");

        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution flowListenerNoInput = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS), tenantId, NAMESPACE,
            "trigger-flow-listener-no-inputs");
        Execution flowListener = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS), tenantId, NAMESPACE,
            "trigger-flow-listener");
        Execution flowListenerNamespace = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS), tenantId, NAMESPACE,
            "trigger-flow-listener-namespace-condition");


        assertThat(flowListener.getTaskRunList().size()).isEqualTo(1);
        assertThat(flowListener.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(flowListener.getTaskRunList().getFirst().getOutputs().get("value")).isEqualTo("childs: from parents: " + execution.getId());
        assertThat(flowListener.getTrigger().getVariables().get("executionId")).isEqualTo(execution.getId());
        assertThat(flowListener.getTrigger().getVariables().get("namespace")).isEqualTo(NAMESPACE);
        assertThat(flowListener.getTrigger().getVariables().get("flowId")).isEqualTo("trigger-flow");

        assertThat(flowListenerNoInput.getTaskRunList().size()).isEqualTo(1);
        assertThat(flowListenerNoInput.getTrigger().getVariables().get("executionId")).isEqualTo(execution.getId());
        assertThat(flowListenerNoInput.getTrigger().getVariables().get("namespace")).isEqualTo(NAMESPACE);
        assertThat(flowListenerNoInput.getTrigger().getVariables().get("flowId")).isEqualTo("trigger-flow");
        assertThat(flowListenerNoInput.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertThat(flowListenerNamespace.getTaskRunList().size()).isEqualTo(1);
        assertThat(flowListenerNamespace.getTrigger().getVariables().get("namespace")).isEqualTo(NAMESPACE);
        // it will be triggered for 'trigger-flow' or any of the 'trigger-flow-listener*', so we only assert that it's one of them
        assertThat(flowListenerNamespace.getTrigger().getVariables().get("flowId"))
            .satisfiesAnyOf(
                arg -> assertThat(arg).isEqualTo("trigger-flow"),
                arg -> assertThat(arg).isEqualTo("trigger-flow-listener-no-inputs"),
                arg -> assertThat(arg).isEqualTo("trigger-flow-listener")
            );
    }

    public void triggerWithPause() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.trigger.pause", "trigger-flow-with-pause");

        assertThat(execution.getTaskRunList().size()).isEqualTo(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        List<Execution> triggeredExec = runnerUtils.awaitFlowExecutionNumber(
            4,
            MAIN_TENANT,
            "io.kestra.tests.trigger.pause",
            "trigger-flow-listener-with-pause");

        assertThat(triggeredExec.size()).isEqualTo(4);
        List<Execution> sortedExecs = triggeredExec.stream()
            .sorted(Comparator.comparing(e -> e.getState().getEndDate().orElse(Instant.now())))
            .toList();
        assertThat(sortedExecs.get(0).getOutputs().get("status")).isEqualTo("RUNNING");
        assertThat(sortedExecs.get(1).getOutputs().get("status")).isEqualTo("PAUSED");
        assertThat(sortedExecs.get(2).getOutputs().get("status")).isEqualTo("RUNNING");
        assertThat(sortedExecs.get(3).getOutputs().get("status")).isEqualTo("SUCCESS");
    }

    public void triggerWithConcurrencyLimit(String tenantId) throws QueueException, TimeoutException {
        Execution execution1 = runnerUtils.runOneUntilRunning(tenantId, "io.kestra.tests.trigger.concurrency", "trigger-flow-with-concurrency-limit");
        Execution execution2 = runnerUtils.runOne(tenantId, "io.kestra.tests.trigger.concurrency", "trigger-flow-with-concurrency-limit");

        List<Execution> triggeredExec = runnerUtils.awaitFlowExecutionNumber(
            5,
            tenantId,
            "io.kestra.tests.trigger.concurrency",
            "trigger-flow-listener-with-concurrency-limit");

        assertThat(triggeredExec.size()).isEqualTo(5);
        assertThat(triggeredExec.stream().anyMatch(e -> e.getOutputs().get("status").equals("RUNNING") && e.getOutputs().get("executionId").equals(execution1.getId()))).isTrue();
        assertThat(triggeredExec.stream().anyMatch(e -> e.getOutputs().get("status").equals("SUCCESS") && e.getOutputs().get("executionId").equals(execution1.getId()))).isTrue();
        assertThat(triggeredExec.stream().anyMatch(e -> e.getOutputs().get("status").equals("QUEUED") && e.getOutputs().get("executionId").equals(execution2.getId()))).isTrue();
        assertThat(triggeredExec.stream().anyMatch(e -> e.getOutputs().get("status").equals("RUNNING") && e.getOutputs().get("executionId").equals(execution2.getId()))).isTrue();
        assertThat(triggeredExec.stream().anyMatch(e -> e.getOutputs().get("status").equals("SUCCESS") && e.getOutputs().get("executionId").equals(execution2.getId()))).isTrue();
    }
}
