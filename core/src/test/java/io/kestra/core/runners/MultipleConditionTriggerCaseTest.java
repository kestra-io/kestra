package io.kestra.core.runners;

import io.kestra.core.models.flows.State.Type;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.micronaut.context.ApplicationContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.FlowRepositoryInterface;

import io.micronaut.data.model.Pageable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Singleton
public class MultipleConditionTriggerCaseTest {

    public static final String NAMESPACE = "io.kestra.tests.trigger";

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    protected FlowRepositoryInterface flowRepository;

    @Inject
    protected ExecutionRepositoryInterface executionRepository;

    @Inject
    protected ApplicationContext applicationContext;

    public void trigger() throws InterruptedException, TimeoutException, QueueException {
        // first one
        Execution execution = runnerUtils.runOne(MAIN_TENANT, NAMESPACE, "trigger-multiplecondition-flow-a");
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // wait a little to be sure that the trigger is not launching execution
        Thread.sleep(1000);
        ArrayListTotal<Execution> flowBExecutions = executionRepository.findByFlowId(MAIN_TENANT,
            NAMESPACE, "trigger-multiplecondition-flow-b", Pageable.UNPAGED);
        ArrayListTotal<Execution> listenerExecutions = executionRepository.findByFlowId(MAIN_TENANT,
            NAMESPACE, "trigger-multiplecondition-listener", Pageable.UNPAGED);
        assertThat(flowBExecutions).isEmpty();
        assertThat(listenerExecutions).isEmpty();

        // second one
        execution = runnerUtils.runOne(MAIN_TENANT, NAMESPACE, "trigger-multiplecondition-flow-b");
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, NAMESPACE, "trigger-multiplecondition-listener");

        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertThat(triggerExecution.getTrigger().getVariables().get("executionId")).isEqualTo(execution.getId());
        assertThat(triggerExecution.getTrigger().getVariables().get("namespace")).isEqualTo(
            NAMESPACE);
        assertThat(triggerExecution.getTrigger().getVariables().get("flowId")).isEqualTo("trigger-multiplecondition-flow-b");
    }

    public void failed(String tenantId) throws InterruptedException, TimeoutException, QueueException {
        // first one
        Execution execution = runnerUtils.runOne(tenantId, NAMESPACE,
            "trigger-multiplecondition-flow-c");
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // wait a little to be sure that the trigger is not launching execution
        Thread.sleep(1000);
        ArrayListTotal<Execution> byFlowId = executionRepository.findByFlowId(tenantId, NAMESPACE,
            "trigger-multiplecondition-flow-d", Pageable.UNPAGED);
        assertThat(byFlowId).isEmpty();

        // second one
        execution = runnerUtils.runOne(tenantId, NAMESPACE,
            "trigger-multiplecondition-flow-d");
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            tenantId, NAMESPACE, "trigger-flow-listener-namespace-condition");

        // trigger was not done
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void flowTriggerPreconditions() throws TimeoutException, QueueException {

        // flowA
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-a");
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // flowB: we trigger it two times, as flow-trigger-flow-preconditions-flow-listen is configured with resetOnSuccess: false it should be triggered two times
        execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-a");
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-b");
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.preconditions", "flow-trigger-preconditions-flow-listen");

        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(triggerExecution.getTrigger().getVariables().get("outputs")).isNotNull();
        assertThat((Map<String, Object>) triggerExecution.getTrigger().getVariables().get("outputs")).containsEntry("some", "value");
    }

    public void flowTriggerPreconditionsMergeOutputs(String tenantId) throws QueueException, TimeoutException {
        // we do the same as in flowTriggerPreconditions() but we trigger flows in the opposite order to be sure that outputs are merged

        // flowB
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-b");
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // flowA
        execution = runnerUtils.runOne(tenantId, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-a");
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            tenantId, "io.kestra.tests.trigger.preconditions", "flow-trigger-preconditions-flow-listen");

        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(triggerExecution.getTrigger().getVariables().get("outputs")).isNotNull();
        assertThat((Map<String, Object>) triggerExecution.getTrigger().getVariables().get("outputs")).containsEntry("some", "value");
    }

    public void flowTriggerOnPaused() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.trigger.paused",
            "flow-trigger-paused-flow");
        assertThat(execution.getTaskRunList().size()).isEqualTo(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.paused", "flow-trigger-paused-listen");

        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void forEachItemWithFlowTrigger() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.trigger.foreachitem",
            "flow-trigger-for-each-item-parent");
        assertThat(execution.getTaskRunList().size()).isEqualTo(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        List<Execution> childExecutions = runnerUtils.awaitFlowExecutionNumber(5, MAIN_TENANT, "io.kestra.tests.trigger.foreachitem", "flow-trigger-for-each-item-child");
        assertThat(childExecutions).hasSize(5);
        childExecutions.forEach(exec -> {
            assertThat(exec.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
            assertThat(exec.getTaskRunList().size()).isEqualTo(1);
        });

        List<Execution> grandchildExecutions = runnerUtils.awaitFlowExecutionNumber(5, MAIN_TENANT, "io.kestra.tests.trigger.foreachitem", "flow-trigger-for-each-item-grandchild");
        assertThat(grandchildExecutions).hasSize(5);
        grandchildExecutions.forEach(exec -> {
            assertThat(exec.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
            assertThat(exec.getTaskRunList().size()).isEqualTo(2);
        });
    }

    public void flowTriggerMultiplePreconditions() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.trigger.multiple.preconditions",
            "flow-trigger-multiple-preconditions-flow-a");
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.multiple.preconditions", "flow-trigger-multiple-preconditions-flow-listen");
        executionRepository.delete(triggerExecution);
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // we assert that we didn't have any other flow triggered
        assertThrows(RuntimeException.class, () -> runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.multiple.preconditions", "flow-trigger-multiple-preconditions-flow-listen", Duration.ofSeconds(1)));
    }

    public void flowTriggerMultipleConditions() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.trigger.multiple.conditions",
            "flow-trigger-multiple-conditions-flow-a");
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.multiple.conditions", "flow-trigger-multiple-conditions-flow-listen");
        executionRepository.delete(triggerExecution);
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // we assert that we didn't have any other flow triggered
        assertThrows(RuntimeException.class, () -> runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.multiple.conditions", "flow-trigger-multiple-conditions-flow-listen", Duration.ofSeconds(1)));
    }

    public void flowTriggerMixedConditions() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.trigger.mixed.conditions",
            "flow-trigger-mixed-conditions-flow-a");
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.mixed.conditions", "flow-trigger-mixed-conditions-flow-listen");
        executionRepository.delete(triggerExecution);
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // we assert that we didn't have any other flow triggered
        assertThrows(RuntimeException.class, () -> runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.mixed.conditions", "flow-trigger-mixed-conditions-flow-listen", Duration.ofSeconds(1)));
    }
}
