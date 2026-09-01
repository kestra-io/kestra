package io.kestra.executor.statemachine;

import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionEvent;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.ScopedConcurrencyLimit;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.plugin.core.log.Log;

/**
 * Reproduction of kestra-ee#10022: removing a namespace concurrency limit while executions are
 * queued releases exactly one of them and orphans the rest — QUEUED forever with
 * {@code running: 0} and nothing throttling the namespace.
 * <p>
 * Mechanism: queued executions are only ever popped by the release of a slot in a covering
 * scope. After the limit is removed, the pop consumer stamps the popped execution with the
 * <b>currently resolved</b> scopes — now an empty list — so its own termination has nothing to
 * release ({@code release()} short-circuits on empty limits) and the pop chain dies. Fresh
 * executions run unlimited and unstamped, so they never continue the chain either.
 */
class NamespaceLimitRemovalReproTest {

    private static final String NAMESPACE = "qa.orphan";

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    @Test
    void removingTheNamespaceLimitOrphansTheRemainingQueuedExecutions() {
        // Given: a namespace limit of 1 — the first execution runs and claims the namespace
        // slot (stamped), two more queue behind it
        FlowWithSource flow = flowInNamespace();
        harness.registerFlow(flow);
        ScopedConcurrencyLimit namespaceLimit = ScopedConcurrencyLimit.ofNamespace(
            Flows.TENANT,
            NAMESPACE,
            Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build()
        );
        stubLimits(flow, namespaceLimit);

        ExecutorContext first = startExecution(flow);
        Assertions.assertThat(first.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(first.getExecution().getMetadata().getConcurrencyScopes()).containsExactly(namespaceLimit.uid());

        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        handleEvent(second);
        Execution third = Executions.created(flow);
        harness.executionStateStore().save(third);
        handleEvent(third);
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).hasSize(2);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(namespaceLimit)).isEqualTo(1);

        // When: the namespace limit is removed while they wait
        stubLimits(flow);

        // Then: nothing throttles the namespace anymore — a brand-new execution starts
        // immediately (the issue's "a new execution of the same flow starts immediately")
        ExecutorContext fresh = startExecution(flow);
        Assertions.assertThat(fresh.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(fresh.getExecution().getMetadata().getConcurrencyScopes()).isNull();
        // its termination releases nothing and pops nothing: it never claimed a slot
        Assertions.assertThat(release(terminated(flow, fresh.getExecution()))).isEmpty();
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).hasSize(2);

        // When: the original slot holder terminates — its stamp still frees the removed scope
        Optional<Execution> popped = release(terminated(flow, first.getExecution()));

        // Then: the chain still works ONCE — but the popped execution is stamped with the
        // currently resolved scopes, which is now an EMPTY list
        Assertions.assertThat(popped).map(Execution::getId).hasValue(second.getId());
        Assertions.assertThat(popped.get().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(popped.get().getMetadata().getConcurrencyScopes()).isEmpty();
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(namespaceLimit)).isZero();

        // When: the popped execution terminates in turn
        Optional<Execution> next = release(terminated(flow, popped.get()));

        // Then — kestra-ee#10022: its empty stamp means release() short-circuits, so the last
        // queued execution is never popped. It stays QUEUED forever while running is 0 and no
        // limit exists; only a manual unqueue can free it.
        Assertions.assertThat(next)
            .as("the pop chain died with the removed limit: nothing will ever pop the remaining queued execution")
            .isEmpty();
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(namespaceLimit)).isZero();
        Assertions.assertThat(harness.executionQueuedStateStore().queued())
            .extracting(queued -> queued.getExecution().getId())
            .as("orphaned forever (ee#10022) — QUEUED with running: 0 and no limit defined")
            .containsExactly(third.getId());
    }

    // --- fixtures

    private ExecutorContext startExecution(FlowWithSource flow) {
        Execution execution = Executions.created(flow);
        harness.executionStateStore().save(execution);
        return handleEvent(execution);
    }

    private ExecutorContext handleEvent(Execution execution) {
        return harness.executionEventMessageHandler()
            .handle(new ExecutionEvent(execution, ExecutionEventType.CREATED))
            .orElseThrow();
    }

    private Optional<Execution> release(ExecutorContext terminated) {
        return harness.concurrencySlotReleaseProcessor().release(terminated, true);
    }

    private static ExecutorContext terminated(FlowWithSource flow, Execution running) {
        return new ExecutorContext(running, flow).withExecution(running.withState(State.Type.SUCCESS), "test");
    }

    private void stubLimits(FlowWithSource flow, ScopedConcurrencyLimit... limits) {
        Mockito.doReturn(List.of(limits))
            .when(harness.concurrencyLimitResolver())
            .resolveLimits(Mockito.argThat(candidate -> candidate != null && flow.getId().equals(candidate.getId())));
    }

    private static FlowWithSource flowInNamespace() {
        return Flows.of(
            Flows.builder(Log.builder().id("hold").type(Log.class.getName()).message("hello").build())
                .namespace(NAMESPACE)
                .id("orphan-probe")
                .build()
        );
    }
}
