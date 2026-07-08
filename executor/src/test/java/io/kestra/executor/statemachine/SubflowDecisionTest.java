package io.kestra.executor.statemachine;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.SubflowExecution;
import io.kestra.core.runners.SubflowExecutionEnd;
import io.kestra.core.runners.SubflowExecutionResult;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;

import static io.kestra.executor.testkit.ExecutorContextAssert.assertThat;

/**
 * Layer-1 saga matrix for the {@code io.kestra.plugin.core.flow.Subflow} task, driven as explicit
 * cycles through the harness: process the parent → inspect the SubflowExecution the executor asks
 * for → terminate the child the production way (SubflowExecutionEnd → SubflowExecutionResult) →
 * assert the parent's resumption and final state.
 * Twins the decision logic behind FlowCaseTest#waitSuccess/#waitFailed (task-flow YAML over a
 * StandAloneRunner) and SubflowRunnerTest#subflowOutputWithoutWait/#subflowOutputWithWait/
 * #shouldPassNullableInputFromParentToSubflow, each of which boots Micronaut + H2 + real queues.
 * The decision code under test is ExecutorService#handleExecutableTasks,
 * Subflow#createSubflowExecutions/#createSubflowExecutionResult and
 * ExecutableUtils#subflowExecution/#guessState (see SubflowFinalStateTest for the pure
 * guessState truth table). Handler plumbing (kill switches, joinability, output persistence) is
 * covered by SubflowExecutionResultMessageHandlerTest / SubflowExecutionEndMessageHandlerTest and
 * deliberately not repeated here. No Micronaut, no database, no queues.
 */
class SubflowDecisionTest {

    private static final String SUBFLOW_TASK = "launch-subflow";
    private static final String CHILD_FLOW = "simple-child";

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    // --- starting the child

    @Test
    void shouldStartChildAndKeepParentRunningWhenWaitIsDefault() {
        // Given: a registered child flow and a parent whose Subflow task waits (default wait=true)
        registerSimpleChild();
        FlowWithSource parentFlow = registerParent("""
            id: parent-wait
            namespace: io.kestra.tests

            tasks:
              - id: launch-subflow
                type: io.kestra.plugin.core.flow.Subflow
                namespace: io.kestra.tests
                flowId: simple-child
            """);

        // When: the parent execution is processed
        ExecutorContext context = harness.process(parentFlow, Executions.created(parentFlow));

        // Then: the Subflow taskrun is RUNNING and the executor asks for a child execution —
        // no worker task is ever emitted for an executable task
        assertThat(context)
            .hasTaskRunInState(SUBFLOW_TASK, State.Type.RUNNING)
            .executionInState(State.Type.RUNNING)
            .updatedFrom("handleExecutableTasks")
            .hasNoWorkerTasks()
            .hasNoExecutionDelays();

        Assertions.assertThat(context.getSubflowExecutions()).hasSize(1);
        SubflowExecution<?> subflowExecution = context.getSubflowExecutions().getFirst();
        Execution child = subflowExecution.getExecution();
        Assertions.assertThat(child.getNamespace()).isEqualTo("io.kestra.tests");
        Assertions.assertThat(child.getFlowId()).isEqualTo(CHILD_FLOW);
        Assertions.assertThat(child.getFlowRevision()).isEqualTo(1);
        Assertions.assertThat(child.getState().getCurrent()).isEqualTo(State.Type.CREATED);

        // the child carries an ExecutionTrigger pointing back at the parent
        TaskRun parentTaskRun = context.getExecution().findTaskRunsByTaskId(SUBFLOW_TASK).getFirst();
        Assertions.assertThat(child.getTrigger().getType()).isEqualTo("io.kestra.plugin.core.flow.Subflow");
        Assertions.assertThat(child.getTrigger().getId()).isEqualTo(SUBFLOW_TASK);
        Assertions.assertThat(child.getTrigger().getVariables())
            .containsEntry("executionId", context.getExecution().getId())
            .containsEntry("namespace", "io.kestra.tests")
            .containsEntry("flowId", "parent-wait")
            .containsEntry("flowRevision", 1)
            .containsEntry("taskRunId", parentTaskRun.getId())
            .containsEntry("taskId", SUBFLOW_TASK);

        // wait=true: no immediate result — the parent stays RUNNING until the child terminates
        Assertions.assertThat(context.getSubflowExecutionResults()).isEmpty();
    }

    @Test
    void shouldEmitImmediateSuccessResultWhenWaitDisabled() throws Exception {
        // Given: the same shape but wait=false
        registerSimpleChild();
        FlowWithSource parentFlow = registerParent("""
            id: parent-no-wait
            namespace: io.kestra.tests

            tasks:
              - id: launch-subflow
                type: io.kestra.plugin.core.flow.Subflow
                namespace: io.kestra.tests
                flowId: simple-child
                wait: false
            """);

        // When
        ExecutorContext context = harness.process(parentFlow, Executions.created(parentFlow));

        // Then: the child execution is still created...
        Assertions.assertThat(context.getSubflowExecutions()).hasSize(1);
        Execution child = context.getSubflowExecutions().getFirst().getExecution();

        // ...and an immediate SUCCESS result for the parent taskrun lands on the context command
        // object (getSubflowExecutionResults). Actual production behavior: ExecutorService never
        // waits — ExecutionEventMessageHandler then emits this onto the subflow result queue.
        Assertions.assertThat(context.getSubflowExecutionResults()).hasSize(1);
        SubflowExecutionResult result = context.getSubflowExecutionResults().getFirst();
        Assertions.assertThat(result.getState()).isEqualTo(State.Type.SUCCESS);
        Assertions.assertThat(result.getExecutionId()).isEqualTo(child.getId());
        Assertions.assertThat(result.getParentTaskRun().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Assertions.assertThat(result.getParentTaskRun().getTaskId()).isEqualTo(SUBFLOW_TASK);

        // When: the result is joined back (the production path once the queue delivers it)
        harness.executionStateStore().save(context.getExecution());
        ExecutorContext joined = harness.subflowExecutionResultMessageHandler().handle(result).orElseThrow();
        ExecutorContext done = harness.process(parentFlow, joined.getExecution());

        // Then: the parent completes although the child execution never terminated
        assertThat(done)
            .hasTaskRunInState(SUBFLOW_TASK, State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS);
        Assertions.assertThat(child.getState().getCurrent()).isEqualTo(State.Type.CREATED);
    }

    // --- child inputs (real Pebble + real FlowInputOutput)

    @Test
    void shouldResolveDeclaredInputsAndApplyDefaultsWhenParentPassesInputs() {
        // Given: a child declaring one plain input and one defaulted input
        FlowWithSource childFlow = Flows.yaml("""
            id: child-with-inputs
            namespace: io.kestra.tests

            inputs:
              - id: greeting
                type: STRING
              - id: defaulted
                type: STRING
                defaults: fallback

            tasks:
              - id: child-task
                type: io.kestra.plugin.core.log.Log
                message: "{{ inputs.greeting }}"
            """);
        harness.registerFlow(childFlow);
        // and a parent passing the declared input through a Pebble expression
        FlowWithSource parentFlow = registerParent("""
            id: parent-with-inputs
            namespace: io.kestra.tests

            tasks:
              - id: launch-subflow
                type: io.kestra.plugin.core.flow.Subflow
                namespace: io.kestra.tests
                flowId: child-with-inputs
                inputs:
                  greeting: "hello from {{ flow.id }}"
            """);

        // When
        ExecutorContext context = harness.process(parentFlow, Executions.created(parentFlow));

        // Then: the created child execution carries the rendered input and the applied default
        Assertions.assertThat(context.getSubflowExecutions()).hasSize(1);
        Execution child = context.getSubflowExecutions().getFirst().getExecution();
        Assertions.assertThat(child.getInputs())
            .containsEntry("greeting", "hello from parent-with-inputs")
            .containsEntry("defaulted", "fallback");
        assertThat(context).hasTaskRunInState(SUBFLOW_TASK, State.Type.RUNNING);
    }

    // --- unresolvable child flows

    @Test
    void shouldFailTaskRunThenExecutionWhenChildFlowIsMissing() {
        // Given: the referenced child flow is NOT registered in the meta store
        FlowWithSource parentFlow = registerParent("""
            id: parent-missing-child
            namespace: io.kestra.tests

            tasks:
              - id: launch-subflow
                type: io.kestra.plugin.core.flow.Subflow
                namespace: io.kestra.tests
                flowId: nowhere-to-be-found
            """);

        // When
        ExecutorContext cycle1 = harness.process(parentFlow, Executions.created(parentFlow));

        // Then (actual behavior): the taskrun fails within the cycle and the exception rides on
        // the context, but the execution itself is still RUNNING — production persists it and the
        // NEXT execution-event cycle ends it
        assertThat(cycle1)
            .hasTaskRunInState(SUBFLOW_TASK, State.Type.FAILED)
            .executionInState(State.Type.RUNNING)
            .hasNoSubflowExecutions()
            .updatedFrom("handleExecutableTasks");
        Assertions.assertThat(cycle1.getException()).isNotNull();
        Assertions.assertThat(harness.logs())
            .anyMatch(log -> log.getMessage() != null && log.getMessage().contains("Unable to find flow"));

        // When: the persisted execution is re-processed
        ExecutorContext cycle2 = harness.process(parentFlow, cycle1.getExecution());

        // Then: the execution ends FAILED
        assertThat(cycle2)
            .executionInState(State.Type.FAILED)
            .transitioned(State.Type.RUNNING, State.Type.FAILED);
    }

    @Test
    void shouldFailTaskRunWhenChildFlowIsDisabled() {
        // Given: the child flow IS registered, but disabled
        FlowWithSource childFlow = Flows.yaml("""
            id: disabled-child
            namespace: io.kestra.tests
            disabled: true

            tasks:
              - id: child-task
                type: io.kestra.plugin.core.log.Log
                message: never runs
            """);
        harness.registerFlow(childFlow);
        FlowWithSource parentFlow = registerParent("""
            id: parent-disabled-child
            namespace: io.kestra.tests

            tasks:
              - id: launch-subflow
                type: io.kestra.plugin.core.flow.Subflow
                namespace: io.kestra.tests
                flowId: disabled-child
            """);

        // When
        ExecutorContext cycle1 = harness.process(parentFlow, Executions.created(parentFlow));

        // Then: same failure shape as a missing flow — FAILED taskrun, exception on the context
        assertThat(cycle1)
            .hasTaskRunInState(SUBFLOW_TASK, State.Type.FAILED)
            .hasNoSubflowExecutions();
        Assertions.assertThat(cycle1.getException()).isNotNull();
        Assertions.assertThat(harness.logs())
            .anyMatch(log -> log.getMessage() != null && log.getMessage().contains("disabled"));

        // and the follow-up cycle ends the execution FAILED
        ExecutorContext cycle2 = harness.process(parentFlow, cycle1.getExecution());
        assertThat(cycle2).executionInState(State.Type.FAILED);
    }

    // --- child terminal state → parent resumption (Subflow#createSubflowExecutionResult through
    // SubflowExecutionEndMessageHandler, exercising ExecutableUtils#guessState with real config)

    @Test
    void shouldSucceedParentWhenChildSucceeds() throws Exception {
        registerSimpleChild();
        FlowWithSource parentFlow = registerParent("""
            id: parent-child-success
            namespace: io.kestra.tests

            tasks:
              - id: launch-subflow
                type: io.kestra.plugin.core.flow.Subflow
                namespace: io.kestra.tests
                flowId: simple-child
            """);

        ExecutorContext done = terminateChildThenResumeParent(parentFlow, State.Type.SUCCESS);

        Assertions.assertThat(harness.subflowExecutionResultQueue().emitted().getFirst().getState())
            .isEqualTo(State.Type.SUCCESS);
        assertThat(done)
            .hasTaskRunInState(SUBFLOW_TASK, State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS)
            .transitioned(State.Type.RUNNING, State.Type.SUCCESS);
    }

    @Test
    void shouldFailParentWhenChildFailsAndTransmitFailedIsDefault() throws Exception {
        registerSimpleChild();
        // transmitFailed defaults to true
        FlowWithSource parentFlow = registerParent("""
            id: parent-child-failed
            namespace: io.kestra.tests

            tasks:
              - id: launch-subflow
                type: io.kestra.plugin.core.flow.Subflow
                namespace: io.kestra.tests
                flowId: simple-child
            """);

        ExecutorContext done = terminateChildThenResumeParent(parentFlow, State.Type.FAILED);

        Assertions.assertThat(harness.subflowExecutionResultQueue().emitted().getFirst().getState())
            .isEqualTo(State.Type.FAILED);
        assertThat(done)
            .hasTaskRunInState(SUBFLOW_TASK, State.Type.FAILED)
            .executionInState(State.Type.FAILED)
            .transitioned(State.Type.RUNNING, State.Type.FAILED);
    }

    @Test
    void shouldSucceedParentWhenChildFailsWithoutTransmitFailed() throws Exception {
        registerSimpleChild();
        FlowWithSource parentFlow = registerParent("""
            id: parent-no-transmit
            namespace: io.kestra.tests

            tasks:
              - id: launch-subflow
                type: io.kestra.plugin.core.flow.Subflow
                namespace: io.kestra.tests
                flowId: simple-child
                transmitFailed: false
            """);

        ExecutorContext done = terminateChildThenResumeParent(parentFlow, State.Type.FAILED);

        // Actual behavior: guessState short-circuits to SUCCESS when transmitFailed=false, so the
        // parent taskrun — and the parent execution — succeed despite the FAILED child
        Assertions.assertThat(harness.subflowExecutionResultQueue().emitted().getFirst().getState())
            .isEqualTo(State.Type.SUCCESS);
        assertThat(done)
            .hasTaskRunInState(SUBFLOW_TASK, State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS);
    }

    @Test
    void shouldWarnParentWhenChildFailsWithAllowFailure() throws Exception {
        registerSimpleChild();
        // allowFailure downgrades the transmitted FAILED to WARNING (guessState); with
        // allowWarning left false the WARNING sticks and taints the parent execution
        FlowWithSource parentFlow = registerParent("""
            id: parent-allow-failure
            namespace: io.kestra.tests

            tasks:
              - id: launch-subflow
                type: io.kestra.plugin.core.flow.Subflow
                namespace: io.kestra.tests
                flowId: simple-child
                allowFailure: true
            """);

        ExecutorContext done = terminateChildThenResumeParent(parentFlow, State.Type.FAILED);

        Assertions.assertThat(harness.subflowExecutionResultQueue().emitted().getFirst().getState())
            .isEqualTo(State.Type.WARNING);
        assertThat(done)
            .hasTaskRunInState(SUBFLOW_TASK, State.Type.WARNING)
            .executionInState(State.Type.WARNING);
    }

    // --- saga plumbing

    /**
     * Runs the full production termination path: start the parent (scenario-1 style), persist it,
     * report the child's terminal state via SubflowExecutionEnd — the handler calls the real
     * Subflow#createSubflowExecutionResult (guessState included) and emits the result — then join
     * the result back into the parent and process it to its final state.
     */
    private ExecutorContext terminateChildThenResumeParent(FlowWithSource parentFlow, State.Type childState) {
        // start: the executor asks for the child execution and parks the parent RUNNING
        ExecutorContext started = harness.process(parentFlow, Executions.created(parentFlow));
        assertThat(started)
            .hasTaskRunInState(SUBFLOW_TASK, State.Type.RUNNING)
            .executionInState(State.Type.RUNNING);
        Assertions.assertThat(started.getSubflowExecutions()).hasSize(1);
        Assertions.assertThat(started.getSubflowExecutionResults()).isEmpty();
        harness.executionStateStore().save(started.getExecution());

        // the child terminates: production emits a SubflowExecutionEnd carrying the child execution
        Execution child = started.getSubflowExecutions().getFirst().getExecution().withState(childState);
        TaskRun parentTaskRun = started.getExecution().findTaskRunsByTaskId(SUBFLOW_TASK).getFirst();
        harness.subflowExecutionEndMessageHandler().handle(
            new SubflowExecutionEnd(
                child,
                started.getExecution().getId(),
                parentTaskRun.getId(),
                SUBFLOW_TASK,
                childState
            )
        );

        // the handler produced exactly one result via the real createSubflowExecutionResult
        Assertions.assertThat(harness.subflowExecutionResultQueue().emitted()).hasSize(1);
        SubflowExecutionResult result = harness.subflowExecutionResultQueue().emitted().getFirst();

        // join it back into the parent, then process to the final state
        ExecutorContext joined = harness.subflowExecutionResultMessageHandler().handle(result).orElseThrow();
        return harness.process(parentFlow, joined.getExecution());
    }

    // --- fixtures (inline YAML — same graph shape as core/src/test/resources/flows/valids/
    // task-flow.yaml family, with a Log child: the child never actually runs in Layer-1)

    private FlowWithSource registerSimpleChild() {
        FlowWithSource childFlow = Flows.yaml("""
            id: simple-child
            namespace: io.kestra.tests

            tasks:
              - id: child-task
                type: io.kestra.plugin.core.log.Log
                message: hello from the child
            """);
        harness.registerFlow(childFlow);
        return childFlow;
    }

    private FlowWithSource registerParent(String yaml) {
        FlowWithSource parentFlow = Flows.yaml(yaml);
        harness.registerFlow(parentFlow);
        return parentFlow;
    }
}
