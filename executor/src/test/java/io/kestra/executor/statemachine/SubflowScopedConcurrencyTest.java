package io.kestra.executor.statemachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ScopedConcurrencyLimit;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.executor.testkit.Results;
import io.kestra.executor.testkit.ScriptedWorker;

import static io.kestra.executor.testkit.HarnessAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * kestra-ee#11728: a namespace limit with the QUEUE behavior must not queue a Subflow child on
 * the slot its own waiting parent holds, while still counting every execution nobody waits on.
 */
class SubflowScopedConcurrencyTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final String NAMESPACE = "io.kestra.tests";
    private static final String CHILD_NAMESPACE = NAMESPACE + ".child";

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();
    private final List<WorkerTask> heldTasks = new ArrayList<>();

    @Test
    void shouldRunTheChildOnTheSlotOfItsWaitingParent() {
        ScopedConcurrencyLimit namespace = namespaceLimits(Map.of(NAMESPACE, 1)).getFirst();
        FlowWithSource parent = registerParentOf(NAMESPACE, "child", true);
        registerLeaf(NAMESPACE, "child");
        Execution first = Executions.created(parent);
        Execution second = Executions.created(parent);

        harness.run(List.of(first, second), holdingAfterTasks());

        assertThat(stateOfChildOf(first)).as("the child ran on the slot of its waiting parent").isEqualTo(State.Type.SUCCESS);
        assertThat(harness).as("the child released nothing: its parent still holds the only slot")
            .hasExecutionInState(first, State.Type.RUNNING)
            .hasQueuedExactly(second)
            .hasRunning(namespace, 1);

        releaseHeldTasksUntilIdle();

        assertThat(harness)
            .hasExecutionInState(first, State.Type.SUCCESS)
            .hasExecutionInState(second, State.Type.SUCCESS)
            .hasNothingQueued()
            .hasRunning(namespace, 0);
    }

    @Test
    void shouldRunEveryLevelOfANestedSubflowChainOnTheSlotOfTheRoot() {
        ScopedConcurrencyLimit namespace = namespaceLimits(Map.of(NAMESPACE, 1)).getFirst();
        FlowWithSource root = registerParentOf(NAMESPACE, "middle", true);
        registerParentOf(NAMESPACE, "leaf", true, "middle", NAMESPACE);
        registerLeaf(NAMESPACE, "leaf");
        Execution execution = Executions.created(root);

        harness.run(List.of(execution), holdingAfterTasks());
        releaseHeldTasksUntilIdle();

        assertThat(harness)
            .hasExecutionInState(execution, State.Type.SUCCESS)
            .hasNothingQueued()
            .hasRunning(namespace, 0);
    }

    @Test
    void shouldPopAChildQueuedOnItsOwnScopeWhileItsParentNamespaceIsFull() {
        List<ScopedConcurrencyLimit> limits = namespaceLimits(Map.of(NAMESPACE, 2, CHILD_NAMESPACE, 1));
        ScopedConcurrencyLimit parentNamespace = limits.getLast();
        FlowWithSource parent = registerParentOf(NAMESPACE, "child", true, "parent", CHILD_NAMESPACE);
        registerLeaf(CHILD_NAMESPACE, "child");
        Execution first = Executions.created(parent);
        Execution second = Executions.created(parent);

        harness.run(List.of(first, second), holding("after", "work"));

        assertThat(stateOfChildOf(first)).as("the first child runs on the parent namespace slot of its waiting parent").isEqualTo(State.Type.RUNNING);
        assertThat(stateOfChildOf(second)).as("the first child holds the only slot of its own namespace").isEqualTo(State.Type.QUEUED);

        releaseFirstHeldTask("after", "work");

        assertThat(stateOfChildOf(second)).as("popped as soon as its own namespace frees, although the parent namespace is full").isEqualTo(State.Type.RUNNING);
        assertThat(harness).hasExecutionInState(first, State.Type.RUNNING).hasRunning(parentNamespace, 2);

        releaseHeldTasksUntilIdle("after", "work");

        assertThat(harness)
            .hasExecutionInState(first, State.Type.SUCCESS)
            .hasExecutionInState(second, State.Type.SUCCESS)
            .hasNothingQueued();
        limits.forEach(limit -> assertThat(harness).hasRunning(limit, 0));
    }

    @Test
    void shouldQueueTheChildOfAParentThatDoesNotWaitForIt() {
        ScopedConcurrencyLimit namespace = namespaceLimits(Map.of(NAMESPACE, 1)).getFirst();
        FlowWithSource parent = registerParentOf(NAMESPACE, "child", false);
        registerLeaf(NAMESPACE, "child");
        Execution execution = Executions.created(parent);

        harness.run(List.of(execution), holdingAfterTasks());

        assertThat(stateOfChildOf(execution)).as("the parent may end before the child, which needs a slot of its own").isEqualTo(State.Type.QUEUED);

        releaseHeldTasksUntilIdle();

        assertThat(stateOfChildOf(execution)).isEqualTo(State.Type.SUCCESS);
        assertThat(harness).hasExecutionInState(execution, State.Type.SUCCESS).hasNothingQueued().hasRunning(namespace, 0);
    }

    @Test
    void shouldQueueTheReplayOfAChildSinceNoParentWaitsOnIt() throws Exception {
        namespaceLimits(Map.of(NAMESPACE, 1));
        FlowWithSource parent = registerParentOf(NAMESPACE, "child", true);
        FlowWithSource child = registerLeaf(NAMESPACE, "child");
        Execution execution = Executions.created(parent);
        harness.run(List.of(execution), holdingAfterTasks());
        Execution childExecution = harness.executionStateStore().findByIdWithoutAcl(childOf(execution).getId());

        Execution replay = harness.executionService().replay(childExecution, child, null, null, Optional.empty(), false);
        harness.run(List.of(replay), holdingAfterTasks());

        assertThat(harness).as("the parent still holds the only slot").hasExecutionInState(execution, State.Type.RUNNING).hasQueuedExactly(replay);
    }

    /** Every flow of a namespace resolves the limits of that namespace and of its parents, innermost first. */
    private List<ScopedConcurrencyLimit> namespaceLimits(Map<String, Integer> limitByNamespace) {
        List<ScopedConcurrencyLimit> limits = limitByNamespace.entrySet().stream()
            .map(entry -> ScopedConcurrencyLimit.ofNamespace("main", entry.getKey(), Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(entry.getValue()).build()))
            .sorted((a, b) -> b.namespace().length() - a.namespace().length())
            .toList();
        doAnswer(invocation ->
        {
            String flowNamespace = invocation.<FlowInterface> getArgument(0).getNamespace();
            return limits.stream().filter(limit -> flowNamespace.equals(limit.namespace()) || flowNamespace.startsWith(limit.namespace() + ".")).toList();
        }).when(harness.concurrencyLimitResolver()).resolveLimits(any(FlowInterface.class));
        return limits;
    }

    private FlowWithSource registerParentOf(String namespace, String childId, boolean wait) {
        return registerParentOf(namespace, childId, wait, "parent", namespace);
    }

    private FlowWithSource registerParentOf(String namespace, String childId, boolean wait, String id, String childNamespace) {
        FlowWithSource flow = Flows.yaml("""
            id: %s
            namespace: %s
            tasks:
              - id: call
                type: io.kestra.plugin.core.flow.Subflow
                namespace: %s
                flowId: %s
                wait: %s
              - id: after
                type: io.kestra.plugin.core.log.Log
                message: after
            """.formatted(id, namespace, childNamespace, childId, wait));
        harness.registerFlow(flow);
        return flow;
    }

    private FlowWithSource registerLeaf(String namespace, String id) {
        FlowWithSource flow = Flows.yaml("""
            id: %s
            namespace: %s
            tasks:
              - id: work
                type: io.kestra.plugin.core.log.Log
                message: work
            """.formatted(id, namespace));
        harness.registerFlow(flow);
        return flow;
    }

    /** Keeps every {@code after} task running, so a parent holds its slot until it is released. */
    private ScriptedWorker holdingAfterTasks() {
        return holding("after");
    }

    /** Keeps the tasks with these ids running, so their execution holds its slots until they are released. */
    private ScriptedWorker holding(String... taskIds) {
        Set<String> held = Set.of(taskIds);
        return task ->
        {
            if (!held.contains(task.getTask().getId())) {
                return Results.success(task, T0);
            }
            heldTasks.add(task);
            return new WorkerTaskResult(task.getTaskRun().withState(State.Type.RUNNING));
        };
    }

    private void releaseHeldTasksUntilIdle() {
        releaseHeldTasksUntilIdle("after");
    }

    private void releaseHeldTasksUntilIdle(String... taskIds) {
        while (!heldTasks.isEmpty()) {
            releaseFirstHeldTask(taskIds);
        }
    }

    private void releaseFirstHeldTask(String... taskIds) {
        harness.run(List.of(Results.success(heldTasks.removeFirst(), T0)), holding(taskIds));
    }

    private Execution childOf(Execution parent) {
        return harness.executionQueue().emitted().stream()
            .filter(execution -> execution.getTrigger() != null && parent.getId().equals(execution.getTrigger().getVariables().get("executionId")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No child execution was created by execution " + parent.getId()));
    }

    private State.Type stateOfChildOf(Execution parent) {
        return harness.stateOf(childOf(parent));
    }
}
