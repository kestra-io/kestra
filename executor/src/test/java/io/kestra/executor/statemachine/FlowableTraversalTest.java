package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;

import static io.kestra.executor.testkit.ExecutorContextAssert.assertThat;
import static io.kestra.executor.testkit.HarnessAssert.assertThat;

/**
 * Layer-1 flowable-task traversal matrix, driven as explicit sagas — process → start the flowable
 */
class FlowableTraversalTest {

    private static final Instant ATTEMPT_END = Instant.parse("2026-07-06T10:00:00Z");

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    // --- Sequential

    @Test
    void shouldEmitSequentialChildrenStrictlyOneAfterTheOtherWhenEachSucceeds() throws Exception {
        // Given: a Sequential parent with two children — the executor first emits the parent
        // flowable (production never sends it to a worker; the handler flips it to RUNNING)
        FlowWithSource flow = sequentialFlow("sequential-traversal");
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));

        assertThat(cycle1)
            .hasWorkerTaskFor("parent")
            .executionInState(State.Type.RUNNING);
        assertThat(cycle1).hasWorkerTasksExactly("parent");

        // When: the flowable parent starts running
        ExecutorContext cycle2 = startFlowable(flow, cycle1, "parent");

        assertThat(cycle2).as("the first child is emitted ALONE — the second child has no taskrun yet").hasWorkerTasksExactly("child-1");
        assertThat(cycle2)
            .hasTaskRunInState("parent", State.Type.RUNNING)
            .executionInState(State.Type.RUNNING);
        assertThat(cycle2).hasNoTaskRunFor("child-2");

        // When: the first child succeeds
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.success(emittedWorkerTask(cycle2, "child-1"), ATTEMPT_END)
        );

        assertThat(cycle3).as("only now is the second child emitted").hasWorkerTasksExactly("child-2");
        assertThat(cycle3)
            .hasTaskRunInState("child-1", State.Type.SUCCESS)
            .executionInState(State.Type.RUNNING);

        // When: the second child succeeds
        ExecutorContext cycle4 = harness.processResult(
            flow,
            cycle3,
            Results.success(emittedWorkerTask(cycle3, "child-2"), ATTEMPT_END.plusSeconds(60))
        );

        assertThat(cycle4).as("the parent flowable terminates SUCCESS, then the execution ends SUCCESS")
            .hasTaskRunInState("child-2", State.Type.SUCCESS)
            .hasTaskRunInState("parent", State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS)
            .transitioned(State.Type.RUNNING, State.Type.SUCCESS)
            .hasNoWorkerTasks();
        // full saga ordering over the taskrun list: parent, then children in declaration order
        assertThat(cycle4).hasTaskRunsExactly("parent", "child-1", "child-2");

        // side channels stayed clean — everything went through the command object
        assertThat(harness).emittedNoKills();
        assertThat(harness).emittedNoLoopEvents();
    }

    @Test
    void shouldFailParentAndNeverEmitSecondChildWhenFirstSequentialChildFails() throws Exception {
        // Given: the same Sequential flow, first child emitted
        FlowWithSource flow = sequentialFlow("sequential-child-failure");
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        ExecutorContext cycle2 = startFlowable(flow, cycle1, "parent");
        assertThat(cycle2).hasWorkerTasksExactly("child-1");

        // When: the first child fails
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.failed(emittedWorkerTask(cycle2, "child-1"), ATTEMPT_END)
        );

        assertThat(cycle3).as("the parent flowable resolves FAILED, the execution ends FAILED, and the second child was never emitted — no taskrun for it exists at all")
            .hasTaskRunInState("child-1", State.Type.FAILED)
            .hasTaskRunInState("parent", State.Type.FAILED)
            .executionInState(State.Type.FAILED)
            .transitioned(State.Type.RUNNING, State.Type.FAILED)
            .hasNoWorkerTasks();
        assertThat(cycle3).hasNoTaskRunFor("child-2");
        Assertions.assertThat(cycle3.getExecution().getTaskRunList()).hasSize(2);
    }

    // --- Parallel

    @Test
    void shouldEmitAllParallelChildrenUpfrontWhenConcurrentIsDefault() throws Exception {
        // Given: a Parallel parent with 3 children and the default concurrent=0 (no cap)
        FlowWithSource flow = parallelFlow("parallel-default", 0);
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        assertThat(cycle1).hasWorkerTasksExactly("parent");

        // When: the flowable parent starts running
        ExecutorContext cycle2 = startFlowable(flow, cycle1, "parent");

        Assertions.assertThat(emittedTaskIds(cycle2)).as("ALL 3 children are emitted at once, before any result came back")
            .containsExactlyInAnyOrder("c1", "c2", "c3");
        assertThat(cycle2).executionInState(State.Type.RUNNING);

        // When: the first child succeeds
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.success(emittedWorkerTask(cycle2, "c1"), ATTEMPT_END)
        );

        // Then (ACTUAL executor behavior): the two still-pending siblings are RE-EMITTED —
        // handleWorkerTasks emits a worker task for every CREATED taskrun on every cycle; in
        // production the handler moves emitted taskruns to SUBMITTED (which this Layer-1 saga,
        // like the recording queue, does not), and dedup happens downstream of the executor
        Assertions.assertThat(emittedTaskIds(cycle3)).containsExactlyInAnyOrder("c2", "c3");
        assertThat(cycle3)
            .hasTaskRunInState("c1", State.Type.SUCCESS)
            .executionInState(State.Type.RUNNING);

        // When: the remaining children succeed one after the other
        ExecutorContext cycle4 = harness.processResult(
            flow,
            cycle3,
            Results.success(emittedWorkerTask(cycle3, "c2"), ATTEMPT_END.plusSeconds(60))
        );
        assertThat(cycle4).hasWorkerTasksExactly("c3");
        ExecutorContext cycle5 = harness.processResult(
            flow,
            cycle4,
            Results.success(emittedWorkerTask(cycle4, "c3"), ATTEMPT_END.plusSeconds(120))
        );

        assertThat(cycle5).as("the parent flowable terminates SUCCESS, then the execution ends SUCCESS")
            .hasTaskRunInState("parent", State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS)
            .transitioned(State.Type.RUNNING, State.Type.SUCCESS)
            .hasNoWorkerTasks();
    }

    @Test
    void shouldEmitOneParallelChildAtATimeWhenConcurrentIsOne() throws Exception {
        // Given: the same 3-children Parallel but capped with concurrent=1
        FlowWithSource flow = parallelFlow("parallel-concurrent-one", 1);
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));

        // When: the flowable parent starts running
        ExecutorContext cycle2 = startFlowable(flow, cycle1, "parent");

        assertThat(cycle2).as(
            "only the FIRST child is emitted — resolveParallelNexts limits the batch to concurrent minus already-running, and further nexts are held back while a created child is still pending"
        ).hasWorkerTasksExactly("c1");
        assertThat(cycle2).hasNoTaskRunFor("c2");
        assertThat(cycle2).hasNoTaskRunFor("c3");

        // When/Then: each completion releases exactly the next child, strictly one at a time
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.success(emittedWorkerTask(cycle2, "c1"), ATTEMPT_END)
        );
        assertThat(cycle3).hasWorkerTasksExactly("c2");
        assertThat(cycle3).hasNoTaskRunFor("c3");

        ExecutorContext cycle4 = harness.processResult(
            flow,
            cycle3,
            Results.success(emittedWorkerTask(cycle3, "c2"), ATTEMPT_END.plusSeconds(60))
        );
        assertThat(cycle4).hasWorkerTasksExactly("c3");

        ExecutorContext cycle5 = harness.processResult(
            flow,
            cycle4,
            Results.success(emittedWorkerTask(cycle4, "c3"), ATTEMPT_END.plusSeconds(120))
        );
        assertThat(cycle5)
            .hasTaskRunInState("parent", State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS)
            .hasNoWorkerTasks();
    }

    // --- If

    @Test
    void shouldEmitThenBranchAndNeverElseBranchWhenIfConditionIsTrue() throws Exception {
        // Given: an If task whose condition renders truthy through the real Pebble engine
        FlowWithSource flow = ifFlow("if-condition-true", "{{ true }}");
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        assertThat(cycle1).hasWorkerTasksExactly("parent");

        // When: the If parent starts running
        ExecutorContext cycle2 = startFlowable(flow, cycle1, "parent");

        assertThat(cycle2).as("the then-branch task is emitted, the else-branch never gets a taskrun").hasWorkerTasksExactly("then-task");
        assertThat(cycle2).hasNoTaskRunFor("else-task");

        // When: the then-branch task succeeds
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.success(emittedWorkerTask(cycle2, "then-task"), ATTEMPT_END)
        );

        assertThat(cycle3).as("SUCCESS all the way up, and still no trace of the else branch")
            .hasTaskRunInState("then-task", State.Type.SUCCESS)
            .hasTaskRunInState("parent", State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS)
            .hasNoWorkerTasks();
        assertThat(cycle3).hasNoTaskRunFor("else-task");
    }

    @Test
    void shouldEmitElseBranchAndNeverThenBranchWhenIfConditionIsFalse() throws Exception {
        // Given: the same If flow with a condition rendering falsy
        FlowWithSource flow = ifFlow("if-condition-false", "{{ false }}");
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));

        // When: the If parent starts running
        ExecutorContext cycle2 = startFlowable(flow, cycle1, "parent");

        assertThat(cycle2).as("the else-branch task is emitted, the then-branch never gets a taskrun").hasWorkerTasksExactly("else-task");
        assertThat(cycle2).hasNoTaskRunFor("then-task");

        // When: the else-branch task succeeds
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.success(emittedWorkerTask(cycle2, "else-task"), ATTEMPT_END)
        );

        assertThat(cycle3).as("SUCCESS, then-branch never materialized")
            .hasTaskRunInState("else-task", State.Type.SUCCESS)
            .hasTaskRunInState("parent", State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS);
        assertThat(cycle3).hasNoTaskRunFor("then-task");
    }

    // --- Switch

    @Test
    void shouldEmitMatchingCaseBranchWhenSwitchValueMatchesCase() throws Exception {
        // Given: a Switch whose value matches the FIRST case
        FlowWithSource flow = switchFlow("switch-match", "FIRST");
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        assertThat(cycle1).hasWorkerTasksExactly("parent");

        // When: the Switch parent starts running
        ExecutorContext cycle2 = startFlowable(flow, cycle1, "parent");

        assertThat(cycle2).as("only the matching branch is emitted — neither the other case nor defaults run").hasWorkerTasksExactly("first-case-task");
        assertThat(cycle2).hasNoTaskRunFor("second-case-task");
        assertThat(cycle2).hasNoTaskRunFor("default-task");

        // When: the branch succeeds
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.success(emittedWorkerTask(cycle2, "first-case-task"), ATTEMPT_END)
        );

        assertThat(cycle3).as("SUCCESS, and the other branches never materialized")
            .hasTaskRunInState("first-case-task", State.Type.SUCCESS)
            .hasTaskRunInState("parent", State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS);
        assertThat(cycle3).hasNoTaskRunFor("second-case-task");
        assertThat(cycle3).hasNoTaskRunFor("default-task");
    }

    @Test
    void shouldEmitDefaultsBranchWhenSwitchValueMatchesNoCase() throws Exception {
        // Given: a Switch whose value matches no declared case
        FlowWithSource flow = switchFlow("switch-no-match", "SOMETHING-ELSE");
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));

        // When: the Switch parent starts running
        ExecutorContext cycle2 = startFlowable(flow, cycle1, "parent");

        assertThat(cycle2).as("the defaults branch is emitted, no case branch gets a taskrun").hasWorkerTasksExactly("default-task");
        assertThat(cycle2).hasNoTaskRunFor("first-case-task");
        assertThat(cycle2).hasNoTaskRunFor("second-case-task");

        // When: the defaults branch succeeds
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.success(emittedWorkerTask(cycle2, "default-task"), ATTEMPT_END)
        );

        assertThat(cycle3).as("SUCCESS through the defaults branch only")
            .hasTaskRunInState("default-task", State.Type.SUCCESS)
            .hasTaskRunInState("parent", State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS);
    }

    // --- AllowFailure

    @Test
    void shouldEndParentInWarningAndStillRunSiblingWhenAllowFailureChildFails() throws Exception {
        // Given: an AllowFailure block with one child, followed by a sibling task
        FlowWithSource flow = allowFailureFlow("allow-failure-child-fails");
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        assertThat(cycle1).hasWorkerTasksExactly("allow-failure-block");

        // When: the AllowFailure parent starts running
        ExecutorContext cycle2 = startFlowable(flow, cycle1, "allow-failure-block");
        assertThat(cycle2).hasWorkerTasksExactly("failing-child");

        // When: the child fails
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.failed(emittedWorkerTask(cycle2, "failing-child"), ATTEMPT_END)
        );

        // Then (intermediate cycle): the parent flowable ends WARNING — allowFailure downgrades
        // the child failure — and the sibling AFTER the block is emitted, execution keeps RUNNING
        assertThat(cycle3).hasWorkerTasksExactly("sibling-task");
        assertThat(cycle3)
            .hasTaskRunInState("failing-child", State.Type.FAILED)
            .hasTaskRunInState("allow-failure-block", State.Type.WARNING)
            .executionInState(State.Type.RUNNING);

        // When: the sibling succeeds
        ExecutorContext cycle4 = harness.processResult(
            flow,
            cycle3,
            Results.success(emittedWorkerTask(cycle3, "sibling-task"), ATTEMPT_END.plusSeconds(60))
        );

        assertThat(cycle4).as("the WARNING taskrun decides the final state — the execution ends WARNING")
            .hasTaskRunInState("sibling-task", State.Type.SUCCESS)
            .executionInState(State.Type.WARNING)
            .transitioned(State.Type.RUNNING, State.Type.WARNING)
            .hasNoWorkerTasks();
    }

    // --- Dag

    @Test
    void shouldEmitDependentDagTaskOnlyAfterItsDependencySucceeds() throws Exception {
        // Given: a two-task DAG where task-b dependsOn task-a
        FlowWithSource flow = dagFlow("dag-dependency-order");
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        assertThat(cycle1).hasWorkerTasksExactly("parent");

        // When: the Dag parent starts running
        ExecutorContext cycle2 = startFlowable(flow, cycle1, "parent");

        assertThat(cycle2).as("only task-a is emitted — task-b is filtered out until its dependency terminates").hasWorkerTasksExactly("task-a");
        assertThat(cycle2).hasNoTaskRunFor("task-b");

        // When: task-a succeeds
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.success(emittedWorkerTask(cycle2, "task-a"), ATTEMPT_END)
        );

        assertThat(cycle3).as("only now is task-b emitted").hasWorkerTasksExactly("task-b");
        assertThat(cycle3)
            .hasTaskRunInState("task-a", State.Type.SUCCESS)
            .executionInState(State.Type.RUNNING);

        // When: task-b succeeds
        ExecutorContext cycle4 = harness.processResult(
            flow,
            cycle3,
            Results.success(emittedWorkerTask(cycle3, "task-b"), ATTEMPT_END.plusSeconds(60))
        );

        assertThat(cycle4).as("the DAG parent terminates SUCCESS, then the execution ends SUCCESS")
            .hasTaskRunInState("task-b", State.Type.SUCCESS)
            .hasTaskRunInState("parent", State.Type.SUCCESS)
            .executionInState(State.Type.SUCCESS)
            .hasNoWorkerTasks();
    }

    @Test
    void shouldFailDagParentAndNeverEmitDependentTaskWhenDependencyFails() throws Exception {
        // Given: the same DAG, task-a emitted
        FlowWithSource flow = dagFlow("dag-branch-failure");
        ExecutorContext cycle1 = harness.process(flow, Executions.created(flow));
        ExecutorContext cycle2 = startFlowable(flow, cycle1, "parent");
        assertThat(cycle2).hasWorkerTasksExactly("task-a");

        // When: task-a fails
        ExecutorContext cycle3 = harness.processResult(
            flow,
            cycle2,
            Results.failed(emittedWorkerTask(cycle2, "task-a"), ATTEMPT_END)
        );

        assertThat(cycle3).as("the branch failure fails the DAG parent and the execution; the dependent task never gets a taskrun")
            .hasTaskRunInState("task-a", State.Type.FAILED)
            .hasTaskRunInState("parent", State.Type.FAILED)
            .executionInState(State.Type.FAILED)
            .transitioned(State.Type.RUNNING, State.Type.FAILED)
            .hasNoWorkerTasks();
        assertThat(cycle3).hasNoTaskRunFor("task-b");
    }

    // --- saga helpers

    /**
     * Mirrors the production handler's "flowable attempt state transition to running" step:
     * ExecutorService#handleWorkerTasks emits every CREATED taskrun — flowables included — but
     * a flowable is never sent to a worker (Task#isSendToWorkerTask is false); the
     * ExecutionEventMessageHandler merges a RUNNING WorkerTaskResult for it instead. The harness
     * stops at the emission, so the saga performs that transition explicitly.
     */
    private ExecutorContext startFlowable(FlowWithSource flow, ExecutorContext previous, String taskId) throws Exception {
        TaskRun created = emittedWorkerTask(previous, taskId).workerTask().getTaskRun();
        TaskRun running = created
            .withAttempts(
                List.of(
                    TaskRunAttempt.builder().state(new State().withState(State.Type.RUNNING)).build()
                )
            )
            .withState(State.Type.RUNNING);
        return harness.processResult(flow, previous, new WorkerTaskResult(running));
    }

    private static ExecutorContext.ExecutorWorkerTask emittedWorkerTask(ExecutorContext context, String taskId) {
        return context.getWorkerTasks().stream()
            .filter(workerTask -> taskId.equals(workerTask.workerTask().getTaskRun().getTaskId()))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError(
                    "no worker task emitted for <" + taskId + "> (emitted: " + emittedTaskIds(context) + ")"
                )
            );
    }

    private static List<String> emittedTaskIds(ExecutorContext context) {
        return context.getWorkerTasks().stream()
            .map(workerTask -> workerTask.workerTask().getTaskRun().getTaskId())
            .toList();
    }

    // --- fixtures (inline YAML — same graph shapes as core/src/test/resources/flows/valids/
    // sequential.yaml / parallel.yaml / if-condition.yaml / switch.yaml / allow-failure.yaml /
    // dag.yaml, with Log leaf tasks everywhere: in Layer-1 sagas the worker is the test and
    // outcomes are scripted via Results)

    private FlowWithSource sequentialFlow(String id) {
        FlowWithSource flow = Flows.yaml("""
            id: %s
            namespace: io.kestra.tests

            tasks:
              - id: parent
                type: io.kestra.plugin.core.flow.Sequential
                tasks:
                  - id: child-1
                    type: io.kestra.plugin.core.log.Log
                    message: first
                  - id: child-2
                    type: io.kestra.plugin.core.log.Log
                    message: second
            """.formatted(id));
        harness.registerFlow(flow);
        return flow;
    }

    private FlowWithSource parallelFlow(String id, int concurrent) {
        FlowWithSource flow = Flows.yaml("""
            id: %s
            namespace: io.kestra.tests

            tasks:
              - id: parent
                type: io.kestra.plugin.core.flow.Parallel
                concurrent: %d
                tasks:
                  - id: c1
                    type: io.kestra.plugin.core.log.Log
                    message: one
                  - id: c2
                    type: io.kestra.plugin.core.log.Log
                    message: two
                  - id: c3
                    type: io.kestra.plugin.core.log.Log
                    message: three
            """.formatted(id, concurrent));
        harness.registerFlow(flow);
        return flow;
    }

    private FlowWithSource ifFlow(String id, String condition) {
        FlowWithSource flow = Flows.yaml("""
            id: %s
            namespace: io.kestra.tests

            tasks:
              - id: parent
                type: io.kestra.plugin.core.flow.If
                condition: "%s"
                then:
                  - id: then-task
                    type: io.kestra.plugin.core.log.Log
                    message: condition was true
                else:
                  - id: else-task
                    type: io.kestra.plugin.core.log.Log
                    message: condition was false
            """.formatted(id, condition));
        harness.registerFlow(flow);
        return flow;
    }

    private FlowWithSource switchFlow(String id, String value) {
        FlowWithSource flow = Flows.yaml("""
            id: %s
            namespace: io.kestra.tests

            tasks:
              - id: parent
                type: io.kestra.plugin.core.flow.Switch
                value: "%s"
                cases:
                  FIRST:
                    - id: first-case-task
                      type: io.kestra.plugin.core.log.Log
                      message: first case
                  SECOND:
                    - id: second-case-task
                      type: io.kestra.plugin.core.log.Log
                      message: second case
                defaults:
                  - id: default-task
                    type: io.kestra.plugin.core.log.Log
                    message: defaults
            """.formatted(id, value));
        harness.registerFlow(flow);
        return flow;
    }

    private FlowWithSource allowFailureFlow(String id) {
        FlowWithSource flow = Flows.yaml("""
            id: %s
            namespace: io.kestra.tests

            tasks:
              - id: allow-failure-block
                type: io.kestra.plugin.core.flow.AllowFailure
                tasks:
                  - id: failing-child
                    type: io.kestra.plugin.core.log.Log
                    message: fails via Results
              - id: sibling-task
                type: io.kestra.plugin.core.log.Log
                message: still runs
            """.formatted(id));
        harness.registerFlow(flow);
        return flow;
    }

    private FlowWithSource dagFlow(String id) {
        FlowWithSource flow = Flows.yaml("""
            id: %s
            namespace: io.kestra.tests

            tasks:
              - id: parent
                type: io.kestra.plugin.core.flow.Dag
                tasks:
                  - task:
                      id: task-a
                      type: io.kestra.plugin.core.log.Log
                      message: a
                  - task:
                      id: task-b
                      type: io.kestra.plugin.core.log.Log
                      message: b
                    dependsOn:
                      - task-a
            """.formatted(id));
        harness.registerFlow(flow);
        return flow;
    }
}
