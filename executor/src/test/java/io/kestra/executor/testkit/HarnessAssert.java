package io.kestra.executor.testkit;

import java.util.List;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.core.runners.ScopedConcurrencyLimit;

/**
 * AssertJ vocabulary over the harness's in-memory state — the running counters, the queued store,
 * the side channels — so a saga reads the outcome instead of the fakes' getters.
 */
public class HarnessAssert extends AbstractAssert<HarnessAssert, ExecutorTestHarness> {
    private HarnessAssert(ExecutorTestHarness actual) {
        super(actual, HarnessAssert.class);
    }

    public static HarnessAssert assertThat(ExecutorTestHarness actual) {
        return new HarnessAssert(actual);
    }

    /** The persisted state of {@code execution}, as the store has it now. */
    public HarnessAssert hasExecutionInState(Execution execution, State.Type expected) {
        Assertions.assertThat(actual.stateOf(execution))
            .as(described("persisted state of execution <%s>", execution.getId()))
            .isEqualTo(expected);
        return this;
    }

    public HarnessAssert hasRunning(FlowInterface flow, int expected) {
        Assertions.assertThat(actual.concurrencyLimitStateStore().running(flow))
            .as(described("running counter of flow <%s>", flow.getId()))
            .isEqualTo(expected);
        return this;
    }

    public HarnessAssert hasNothingQueued() {
        Assertions.assertThat(queuedIds()).as(described("queued executions")).isEmpty();
        return this;
    }

    /** Exactly these executions are queued, in this order — the order a release pops them in. */
    public HarnessAssert hasQueuedExactly(Execution... executions) {
        Assertions.assertThat(queuedIds())
            .as(described("queued executions, in arrival order"))
            .containsExactly(java.util.Arrays.stream(executions).map(Execution::getId).toArray(String[]::new));
        return this;
    }

    public HarnessAssert hasQueuedCount(int expected) {
        Assertions.assertThat(queuedIds()).as(described("queued executions")).hasSize(expected);
        return this;
    }

    public HarnessAssert hasPendingDelays(int expected) {
        Assertions.assertThat(actual.executionDelayStateStore().pending()).as(described("pending execution delays")).hasSize(expected);
        return this;
    }

    public HarnessAssert emittedNoKills() {
        Assertions.assertThat(actual.kills()).as(described("kill events emitted")).isEmpty();
        return this;
    }

    public HarnessAssert emittedNoLoopEvents() {
        Assertions.assertThat(actual.loopEvents()).as(described("loop events emitted")).isEmpty();
        return this;
    }

    public HarnessAssert hasRunning(ScopedConcurrencyLimit scope, int expected) {
        Assertions.assertThat(actual.concurrencyLimitStateStore().running(scope))
            .as(described("running counter of scope <%s>", scope.uid()))
            .isEqualTo(expected);
        return this;
    }

    public HarnessAssert hasNoPendingSlaMonitors() {
        Assertions.assertThat(actual.slaMonitorStateStore().pending()).as(described("pending SLA monitors")).isEmpty();
        return this;
    }

    /** Nothing reached the side channels: no new execution, command, worker job, subflow result or follow event. */
    public HarnessAssert emittedNothingElse() {
        Assertions.assertThat(actual.executionQueue().emitted()).as(described("executions emitted")).isEmpty();
        Assertions.assertThat(actual.executionCommandQueue().emitted()).as(described("commands emitted")).isEmpty();
        Assertions.assertThat(actual.workerJobEventQueue().emitted()).as(described("worker jobs emitted")).isEmpty();
        Assertions.assertThat(actual.subflowExecutionResultQueue().emitted()).as(described("subflow results emitted")).isEmpty();
        Assertions.assertThat(actual.followExecutionEventQueue().emitted()).as(described("follow events emitted")).isEmpty();
        return this;
    }

    public HarnessAssert emittedNoExecutions() {
        Assertions.assertThat(actual.executionQueue().emitted()).as(described("executions emitted")).isEmpty();
        return this;
    }

    public HarnessAssert emittedNoCommands() {
        Assertions.assertThat(actual.executionCommandQueue().emitted()).as(described("commands emitted")).isEmpty();
        return this;
    }

    public HarnessAssert emittedNoWorkerJobs() {
        Assertions.assertThat(actual.workerJobEventQueue().emitted()).as(described("worker jobs emitted")).isEmpty();
        return this;
    }

    public HarnessAssert emittedNoSubflowResults() {
        Assertions.assertThat(actual.subflowExecutionResultQueue().emitted()).as(described("subflow results emitted")).isEmpty();
        return this;
    }

    public HarnessAssert emittedNoFollowEvents() {
        Assertions.assertThat(actual.followExecutionEventQueue().emitted()).as(described("follow events emitted")).isEmpty();
        return this;
    }

    private List<String> queuedIds() {
        return actual.executionQueuedStateStore().queued().stream().map(ExecutionQueued::getExecution).map(Execution::getId).toList();
    }

    private String described(String detail, Object... args) {
        String what = detail.formatted(args);
        return descriptionText().isEmpty() ? what : descriptionText() + " — " + what;
    }
}
