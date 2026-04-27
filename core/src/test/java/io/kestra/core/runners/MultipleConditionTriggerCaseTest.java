package io.kestra.core.runners;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.State.Type;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;

import io.micronaut.context.ApplicationContext;
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

    public void flowTriggerPreconditions() throws TimeoutException, QueueException {
        // flowA
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // flowB: we trigger it two times, as flow-trigger-flow-preconditions-flow-listen is configured with resetOnSuccess: false it should be triggered two times
        execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-b"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.preconditions", "flow-trigger-preconditions-flow-listen"
        );

        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(triggerExecution.getTrigger().getVariables().get("outputs")).isNotNull();
        assertThat((Map<String, Object>) triggerExecution.getTrigger().getVariables().get("outputs")).containsEntry("some", "value");
    }

    public void flowTriggerPreconditionsMergeOutputs(String tenantId) throws QueueException, TimeoutException {
        // we do the same as in flowTriggerPreconditions() but we trigger flows in the opposite order to be sure that outputs are merged

        // flowB
        Execution execution = runnerUtils.runOne(
            tenantId, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-b"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // flowA
        execution = runnerUtils.runOne(
            tenantId, "io.kestra.tests.trigger.preconditions",
            "flow-trigger-preconditions-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            tenantId, "io.kestra.tests.trigger.preconditions", "flow-trigger-preconditions-flow-listen"
        );

        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(triggerExecution.getTrigger().getVariables().get("outputs")).isNotNull();
        var outputs = (Map<String, Object>) triggerExecution.getTrigger().getVariables().get("outputs");
        assertThat(outputs).containsKey("io.kestra.tests.trigger.preconditions");
        outputs = (Map<String, Object>) outputs.get("io.kestra.tests.trigger.preconditions");
        assertThat(outputs).containsKey("flow-trigger-preconditions-flow-b");
        outputs = (Map<String, Object>) outputs.get("flow-trigger-preconditions-flow-b");
        assertThat(outputs).containsEntry("some", "value");
    }

    public void flowTriggerOnPaused() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.paused",
            "flow-trigger-paused-flow"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(2);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.paused", "flow-trigger-paused-listen"
        );

        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void flowTriggerMultiplePreconditions() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.multiple.preconditions",
            "flow-trigger-multiple-preconditions-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.multiple.preconditions", "flow-trigger-multiple-preconditions-flow-listen"
        );
        executionRepository.delete(triggerExecution);
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // we assert that we didn't have any other flow triggered
        assertThrows(
            RuntimeException.class, () -> runnerUtils.awaitFlowExecution(
                e -> e.getState().getCurrent().equals(Type.SUCCESS),
                MAIN_TENANT, "io.kestra.tests.trigger.multiple.preconditions", "flow-trigger-multiple-preconditions-flow-listen", Duration.ofSeconds(3)
            )
        );
    }

    public void flowTriggerMultipleConditions() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.multiple.conditions",
            "flow-trigger-multiple-conditions-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.multiple.conditions", "flow-trigger-multiple-conditions-flow-listen"
        );
        executionRepository.delete(triggerExecution);
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // we assert that we didn't have any other flow triggered
        assertThrows(
            RuntimeException.class, () -> runnerUtils.awaitFlowExecution(
                e -> e.getState().getCurrent().equals(Type.SUCCESS),
                MAIN_TENANT, "io.kestra.tests.trigger.multiple.conditions", "flow-trigger-multiple-conditions-flow-listen", Duration.ofSeconds(3)
            )
        );
    }

    public void flowTriggerWhenCondition() throws TimeoutException, QueueException {
        // Run flow-a which has label "source: when-test"
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.when.condition",
            "flow-trigger-when-condition-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // The listener uses `when: '{{ labels.source == "when-test" }}'` instead of conditions;
        // it should fire because flow-a carries the matching label.
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.when.condition", "flow-trigger-when-condition-flow-listen"
        );
        executionRepository.delete(triggerExecution);
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // No further execution should be triggered
        assertThrows(
            RuntimeException.class, () -> runnerUtils.awaitFlowExecution(
                e -> e.getState().getCurrent().equals(Type.SUCCESS),
                MAIN_TENANT, "io.kestra.tests.trigger.when.condition", "flow-trigger-when-condition-flow-listen", Duration.ofSeconds(3)
            )
        );
    }

    public void flowTriggerDependsOn() throws TimeoutException, QueueException {
        // flowA
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.depends.on",
            "flow-trigger-depends-on-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // flowA again and flowB
        execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.depends.on",
            "flow-trigger-depends-on-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.depends.on",
            "flow-trigger-depends-on-flow-b"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.depends.on", "flow-trigger-depends-on-flow-listen"
        );

        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void flowTriggerMultipleDependsOn() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.multiple.depends.on",
            "flow-trigger-multiple-depends-on-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.multiple.depends.on", "flow-trigger-multiple-depends-on-flow-listen"
        );
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        executionRepository.delete(triggerExecution); // delete the exec so we can await again

        // second run, by default it would fire again
        execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.multiple.depends.on",
            "flow-trigger-multiple-depends-on-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.multiple.depends.on", "flow-trigger-multiple-depends-on-flow-listen"
        );
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void flowTriggerDependsOnFireOnceTrue() throws TimeoutException, QueueException {
        // First run: both conditions met, trigger fires
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.fire.once.true",
            "flow-trigger-fire-once-true-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.fire.once.true",
            "flow-trigger-fire-once-true-flow-b"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.fire.once.true", "flow-trigger-fire-once-true-flow-listen"
        );
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // Second run: with fireOnce: true the window was cleared on success,
        // so only flow-a satisfying its condition is not enough to re-trigger
        execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.fire.once.true",
            "flow-trigger-fire-once-true-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertThrows(RuntimeException.class, () -> runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS) && !e.getId().equals(triggerExecution.getId()),
            MAIN_TENANT, "io.kestra.tests.trigger.fire.once.true", "flow-trigger-fire-once-true-flow-listen",
            Duration.ofSeconds(3)
        ));
    }

    public void flowTriggerAnyMode() throws TimeoutException, QueueException {
        // Run only flow-a — flow-b is not run
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.any.mode",
            "flow-trigger-any-mode-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // Trigger fires because mode is ANY and one condition (flow-a) is satisfied
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.any.mode", "flow-trigger-any-mode-flow-listen"
        );
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void flowTriggerAtLeastMode() throws TimeoutException, QueueException {
        // Run flow-a and flow-b (2 out of 3 conditions) — flow-c is not run
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.at.least.mode",
            "flow-trigger-at-least-mode-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // only one condition: trigger should not have been fired yet
         assertThrows(RuntimeException.class, () -> runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.at.least.mode", "flow-trigger-at-least-mode-flow-listen",
             Duration.ofMillis(500)
        ));

        execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.at.least.mode",
            "flow-trigger-at-least-mode-flow-b"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // Trigger fires because mode is AT_LEAST with minSatisfied=2 and 2 conditions are satisfied
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.at.least.mode", "flow-trigger-at-least-mode-flow-listen"
        );
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    public void flowTriggerWithInvalidInputs() throws TimeoutException, QueueException {
        // Run the source flow which has no outputs
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.invalid.inputs",
            "flow-trigger-invalid-inputs-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // The listener trigger tries to render inputs referencing trigger outputs that don't exist.
        // Instead of silently swallowing the error, the trigger should produce a FAILED execution.
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.FAILED),
            MAIN_TENANT, "io.kestra.tests.trigger.invalid.inputs", "flow-trigger-invalid-inputs-flow-listen"
        );
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(triggerExecution.getTaskRunList()).isNullOrEmpty();
    }

    public void flowTriggerMixedConditions() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests.trigger.mixed.conditions",
            "flow-trigger-mixed-conditions-flow-a"
        );
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // trigger is done
        Execution triggerExecution = runnerUtils.awaitFlowExecution(
            e -> e.getState().getCurrent().equals(Type.SUCCESS),
            MAIN_TENANT, "io.kestra.tests.trigger.mixed.conditions", "flow-trigger-mixed-conditions-flow-listen"
        );
        executionRepository.delete(triggerExecution);
        assertThat(triggerExecution.getTaskRunList().size()).isEqualTo(1);
        assertThat(triggerExecution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // we assert that we didn't have any other flow triggered
        assertThrows(
            RuntimeException.class, () -> runnerUtils.awaitFlowExecution(
                e -> e.getState().getCurrent().equals(Type.SUCCESS),
                MAIN_TENANT, "io.kestra.tests.trigger.mixed.conditions", "flow-trigger-mixed-conditions-flow-listen", Duration.ofSeconds(3)
            )
        );
    }
}
