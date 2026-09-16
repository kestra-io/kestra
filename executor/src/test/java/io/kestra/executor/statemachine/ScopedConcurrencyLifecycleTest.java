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
import io.kestra.core.services.ConcurrencyLimitResolver;
import io.kestra.executor.ExecutorContext;
import io.kestra.executor.testkit.Executions;
import io.kestra.executor.testkit.ExecutorTestHarness;
import io.kestra.executor.testkit.Flows;
import io.kestra.plugin.core.log.Log;

import static io.kestra.executor.testkit.ExecutorContextAssert.assertThat;

/**
 * Layer-1 sagas of namespace and tenant scoped concurrency limits (kestra-ee#8660) through the
 * real {@code ExecutionEventMessageHandler} and {@code ConcurrencySlotReleaseProcessor}: the
 * flow → namespace → parent namespaces → tenant evaluation order where the first limit reached
 * defines the behavior, the claim-one-slot-per-scope accounting, multi-scope release, and the
 * cross-flow pop of queued executions when a shared scope frees a slot — no Micronaut, no
 * database. Namespace/tenant limit <b>definitions</b> are an EE feature: the sagas stub the
 * {@link ConcurrencyLimitResolver} spy the way the EE resolver will answer, while
 * {@code ConcurrencyLifecycleTest} keeps pinning the OSS flow-scope-only behavior.
 */
class ScopedConcurrencyLifecycleTest {

    private static final String TENANT = Flows.TENANT;

    private final ExecutorTestHarness harness = ExecutorTestHarness.create();

    // --- the resolver seam

    @Test
    void shouldResolveOnlyTheFlowScopeInOss() {
        // Given: the real OSS resolver
        ConcurrencyLimitResolver resolver = new ConcurrencyLimitResolver();
        FlowWithSource unlimited = Flows.of(logTask());
        FlowWithSource limited = flowInNamespace("io.kestra.tests", "limited", queue(3));

        // When / Then: no limit without flow concurrency, a single FLOW scope with it
        Assertions.assertThat(resolver.resolveLimits(unlimited)).isEmpty();
        Assertions.assertThat(resolver.resolveLimits(limited)).satisfiesExactly(scope ->
        {
            Assertions.assertThat(scope.scope()).isEqualTo(ScopedConcurrencyLimit.Scope.FLOW);
            Assertions.assertThat(scope.uid()).isEqualTo(TENANT + "|io.kestra.tests|" + limited.getId());
            Assertions.assertThat(scope.concurrency().getLimit()).isEqualTo(3);
        });
    }

    // --- evaluation order: flow, namespace, parent namespaces, tenant — first limit reached wins

    @Test
    void shouldApplyTheFirstReachedLimitAcrossNamespaces() {
        // Given: the exact example of kestra-ee#8660 — flow A limits 5 (QUEUE), namespace
        // io.kestra.example1 limits 100 (QUEUE), namespace io.kestra limits 10 (FAIL),
        // flow B defines nothing
        ScopedConcurrencyLimit example1Scope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra.example1", queue(100));
        ScopedConcurrencyLimit ioKestraScope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra", limit(10, Concurrency.Behavior.FAIL));

        FlowWithSource flowA = flowInNamespace("io.kestra.example1", "a", queue(5));
        FlowWithSource flowB = flowInNamespace("io.kestra.example2", "b", null);
        harness.registerFlow(flowA);
        harness.registerFlow(flowB);
        stubLimits(flowA, ScopedConcurrencyLimit.ofFlow(flowA), example1Scope, ioKestraScope);
        stubLimits(flowB, ioKestraScope);

        // Given: 9 running flow B and 1 running flow A
        for (int i = 0; i < 9; i++) {
            startExecution(flowB);
        }
        startExecution(flowA);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(ioKestraScope)).isEqualTo(10);

        // When: running another flow A
        Execution another = Executions.created(flowA);
        harness.executionStateStore().save(another);
        ExecutorContext context = handleEvent(another);

        // Then: the io.kestra namespace limit is the first one reached, so the execution FAILs —
        // the flow (1 < 5) and io.kestra.example1 (1 < 100) limits pass and claim nothing
        assertThat(context).executionInState(State.Type.FAILED).updatedFrom("handleConcurrencyLimit");
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flowA)).isEqualTo(1);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(ioKestraScope)).isEqualTo(10);
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).isEmpty();
    }

    @Test
    void shouldApplyTheFlowBehaviorWhenFlowAndNamespaceLimitsAreBothReached() {
        // Given: a flow limit (QUEUE) and a namespace limit (FAIL), both of 1
        FlowWithSource flow = flowInNamespace("io.kestra.tests", "a", queue(1));
        ScopedConcurrencyLimit namespaceScope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra.tests", limit(1, Concurrency.Behavior.FAIL));
        harness.registerFlow(flow);
        stubLimits(flow, ScopedConcurrencyLimit.ofFlow(flow), namespaceScope);
        startExecution(flow);

        // When: a second execution arrives while both limits are reached
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        ExecutorContext context = handleEvent(second);

        // Then: the flow limit is evaluated first, so its QUEUE behavior wins over FAIL
        assertThat(context).executionInState(State.Type.QUEUED).updatedFrom("handleConcurrencyLimit");
        Assertions.assertThat(harness.executionQueuedStateStore().queued())
            .singleElement()
            .satisfies(queued -> Assertions.assertThat(queued.getExecution().getId()).isEqualTo(second.getId()));
    }

    @Test
    void shouldApplyTheInnermostNamespaceBehaviorBeforeItsParent() {
        // Given: no flow limit; the innermost namespace CANCELs, its parent FAILs, both at 1
        FlowWithSource flow = flowInNamespace("io.kestra.team.a", "a", null);
        ScopedConcurrencyLimit childScope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra.team.a", limit(1, Concurrency.Behavior.CANCEL));
        ScopedConcurrencyLimit parentScope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra", limit(1, Concurrency.Behavior.FAIL));
        harness.registerFlow(flow);
        stubLimits(flow, childScope, parentScope);
        startExecution(flow);

        // When
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        ExecutorContext context = handleEvent(second);

        // Then: innermost first — CANCELLED, not FAILED
        assertThat(context).executionInState(State.Type.CANCELLED).updatedFrom("handleConcurrencyLimit");
    }

    @Test
    void shouldApplyTheTenantLimitWhenEveryNamespaceHasCapacity() {
        // Given: a generous namespace limit and a tenant limit of 1 (FAIL)
        FlowWithSource flow = flowInNamespace("io.kestra.tests", "a", null);
        ScopedConcurrencyLimit namespaceScope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra.tests", queue(10));
        ScopedConcurrencyLimit tenantScope = ScopedConcurrencyLimit.ofTenant(TENANT, limit(1, Concurrency.Behavior.FAIL));
        harness.registerFlow(flow);
        stubLimits(flow, namespaceScope, tenantScope);
        startExecution(flow);

        // When
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        ExecutorContext context = handleEvent(second);

        // Then: the tenant limit, last in the evaluation order, defines the behavior
        assertThat(context).executionInState(State.Type.FAILED).updatedFrom("handleConcurrencyLimit");
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(tenantScope)).isEqualTo(1);
    }

    // --- slot accounting: a running execution claims one slot in every scope, none otherwise

    @Test
    void shouldClaimOneSlotInEveryScopeWhenRunning() {
        // Given: flow, namespace, parent namespace and tenant limits
        FlowWithSource flow = flowInNamespace("io.kestra.team.a", "a", queue(5));
        ScopedConcurrencyLimit childScope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra.team.a", queue(5));
        ScopedConcurrencyLimit parentScope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra", queue(5));
        ScopedConcurrencyLimit tenantScope = ScopedConcurrencyLimit.ofTenant(TENANT, queue(5));
        harness.registerFlow(flow);
        stubLimits(flow, ScopedConcurrencyLimit.ofFlow(flow), childScope, parentScope, tenantScope);

        // When
        ExecutorContext started = startExecution(flow);

        // Then: one slot claimed per scope
        Assertions.assertThat(started.getExecution().getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isEqualTo(1);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(childScope)).isEqualTo(1);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(parentScope)).isEqualTo(1);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(tenantScope)).isEqualTo(1);
    }

    @Test
    void shouldCountExecutionsOfFlowsWithoutOwnLimitTowardTheNamespaceLimit() {
        // Given: a flow without flow-level concurrency, in a namespace limited to 2
        FlowWithSource flow = flowInNamespace("io.kestra.tests", "b", null);
        ScopedConcurrencyLimit namespaceScope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra.tests", queue(2));
        harness.registerFlow(flow);
        stubLimits(flow, namespaceScope);

        // When: two executions fill the namespace, a third arrives
        startExecution(flow);
        startExecution(flow);
        Execution third = Executions.created(flow);
        harness.executionStateStore().save(third);
        ExecutorContext context = handleEvent(third);

        // Then: the third queues on the namespace limit; no flow-scoped counter ever moved
        assertThat(context).executionInState(State.Type.QUEUED).updatedFrom("handleConcurrencyLimit");
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(namespaceScope)).isEqualTo(2);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isZero();
    }

    // --- release: termination frees every scope, then the queue drains across flows

    @Test
    void shouldReleaseEveryScopeOnTermination() {
        // Given: a running execution holding flow + namespace + tenant slots
        FlowWithSource flow = flowInNamespace("io.kestra.tests", "a", queue(5));
        ScopedConcurrencyLimit namespaceScope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra.tests", queue(5));
        ScopedConcurrencyLimit tenantScope = ScopedConcurrencyLimit.ofTenant(TENANT, queue(5));
        harness.registerFlow(flow);
        stubLimits(flow, ScopedConcurrencyLimit.ofFlow(flow), namespaceScope, tenantScope);
        ExecutorContext started = startExecution(flow);

        // When: it terminates
        Optional<Execution> popped = release(terminated(flow, started.getExecution()));

        // Then: every scope counter is back to zero, nothing to pop
        Assertions.assertThat(popped).isEmpty();
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flow)).isZero();
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(namespaceScope)).isZero();
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(tenantScope)).isZero();
    }

    @Test
    void shouldPopQueuedExecutionOfAnotherFlowWhenTheSharedNamespaceSlotFrees() {
        // Given: two flows sharing a namespace limited to 1, no flow-level limits
        ScopedConcurrencyLimit namespaceScope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra.tests", queue(1));
        FlowWithSource flowA = flowInNamespace("io.kestra.tests", "a", null);
        FlowWithSource flowB = flowInNamespace("io.kestra.tests", "b", null);
        harness.registerFlow(flowA);
        harness.registerFlow(flowB);
        stubLimits(flowA, namespaceScope);
        stubLimits(flowB, namespaceScope);

        // Given: A holds the namespace slot, B is queued behind it
        ExecutorContext runningA = startExecution(flowA);
        Execution waitingB = Executions.created(flowB);
        harness.executionStateStore().save(waitingB);
        assertThat(handleEvent(waitingB)).executionInState(State.Type.QUEUED);

        // When: A terminates
        Optional<Execution> popped = release(terminated(flowA, runningA.getExecution()));

        // Then: B — an execution of a different flow — takes over the namespace slot
        Assertions.assertThat(popped)
            .hasValueSatisfying(execution ->
            {
                Assertions.assertThat(execution.getId()).isEqualTo(waitingB.getId());
                Assertions.assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.RUNNING);
            });
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(namespaceScope)).isEqualTo(1);
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).isEmpty();
    }

    @Test
    void shouldSkipCandidateBlockedByItsOwnFlowLimitAndPopTheNextFit() {
        // Given: a namespace limited to 2; flow A additionally limited to 1; flow B unlimited
        ScopedConcurrencyLimit namespaceScope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra.tests", queue(2));
        FlowWithSource flowA = flowInNamespace("io.kestra.tests", "a", queue(1));
        FlowWithSource flowB = flowInNamespace("io.kestra.tests", "b", null);
        FlowWithSource flowC = flowInNamespace("io.kestra.tests", "c", null);
        harness.registerFlow(flowA);
        harness.registerFlow(flowB);
        harness.registerFlow(flowC);
        stubLimits(flowA, ScopedConcurrencyLimit.ofFlow(flowA), namespaceScope);
        stubLimits(flowB, namespaceScope);
        stubLimits(flowC, namespaceScope);

        // Given: A1 and C fill the namespace; A2 queues on its flow limit, then B queues on the namespace
        ExecutorContext runningA1 = startExecution(flowA);
        ExecutorContext runningC = startExecution(flowC);
        Execution waitingA2 = Executions.created(flowA);
        harness.executionStateStore().save(waitingA2);
        assertThat(handleEvent(waitingA2)).executionInState(State.Type.QUEUED);
        Execution waitingB = Executions.created(flowB);
        harness.executionStateStore().save(waitingB);
        assertThat(handleEvent(waitingB)).executionInState(State.Type.QUEUED);

        // When: C terminates, freeing one namespace slot
        Optional<Execution> popped = release(terminated(flowC, runningC.getExecution()));

        // Then: A2 — the oldest candidate — is still blocked by its own flow limit (A1 runs),
        // so it is skipped and B pops instead of starving behind it
        Assertions.assertThat(popped).map(Execution::getId).contains(waitingB.getId());
        Assertions.assertThat(harness.executionQueuedStateStore().queued())
            .singleElement()
            .satisfies(queued -> Assertions.assertThat(queued.getExecution().getId()).isEqualTo(waitingA2.getId()));

        // When: A1 terminates, freeing the flow A slot and a namespace slot
        Optional<Execution> reconsidered = release(terminated(flowA, runningA1.getExecution()));

        // Then: the skipped A2 is reconsidered and finally pops
        Assertions.assertThat(reconsidered).map(Execution::getId).contains(waitingA2.getId());
        Assertions.assertThat(harness.executionQueuedStateStore().queued()).isEmpty();
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(flowA)).isEqualTo(1);
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(namespaceScope)).isEqualTo(2);
    }

    @Test
    void shouldNotPopCandidateOutsideTheFreedScopes() {
        // Given: two namespaces, each limited to 1, each with one running and one queued execution
        ScopedConcurrencyLimit namespace1Scope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra.ns1", queue(1));
        ScopedConcurrencyLimit namespace2Scope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra.ns2", queue(1));
        FlowWithSource flowA = flowInNamespace("io.kestra.ns1", "a", null);
        FlowWithSource flowB = flowInNamespace("io.kestra.ns2", "b", null);
        harness.registerFlow(flowA);
        harness.registerFlow(flowB);
        stubLimits(flowA, namespace1Scope);
        stubLimits(flowB, namespace2Scope);

        ExecutorContext runningA = startExecution(flowA);
        startExecution(flowB);
        // B2 queues first: it is the oldest queued execution overall
        Execution waitingB2 = Executions.created(flowB);
        harness.executionStateStore().save(waitingB2);
        assertThat(handleEvent(waitingB2)).executionInState(State.Type.QUEUED);
        Execution waitingA2 = Executions.created(flowA);
        harness.executionStateStore().save(waitingA2);
        assertThat(handleEvent(waitingA2)).executionInState(State.Type.QUEUED);

        // When: A terminates, freeing only the ns1 slot
        Optional<Execution> popped = release(terminated(flowA, runningA.getExecution()));

        // Then: A2 pops even though B2 queued earlier — B2 shares no scope with the freed slot
        Assertions.assertThat(popped).map(Execution::getId).contains(waitingA2.getId());
        Assertions.assertThat(harness.executionQueuedStateStore().queued())
            .singleElement()
            .satisfies(queued -> Assertions.assertThat(queued.getExecution().getId()).isEqualTo(waitingB2.getId()));
    }

    @Test
    void shouldNotReleaseScopesWhenExecutionWasShortCircuited() {
        // Given: a namespace limit of 1 with the CANCEL behavior, its slot taken
        FlowWithSource flow = flowInNamespace("io.kestra.tests", "a", null);
        ScopedConcurrencyLimit namespaceScope = ScopedConcurrencyLimit.ofNamespace(TENANT, "io.kestra.tests", limit(1, Concurrency.Behavior.CANCEL));
        harness.registerFlow(flow);
        stubLimits(flow, namespaceScope);
        startExecution(flow);

        // Given: a second execution short-circuited CANCELLED at the gate — it never claimed a slot
        Execution second = Executions.created(flow);
        harness.executionStateStore().save(second);
        ExecutorContext cancelled = handleEvent(second);
        assertThat(cancelled).executionInState(State.Type.CANCELLED);

        // When: its termination reaches the release processor (as DefaultExecutor#toExecution does)
        Optional<Execution> popped = release(cancelled);

        // Then: the short-circuit guard holds — no decrement, no pop
        Assertions.assertThat(popped).isEmpty();
        Assertions.assertThat(harness.concurrencyLimitStateStore().running(namespaceScope)).isEqualTo(1);
    }

    // --- fixtures

    /**
     * Create the execution, persist it, and run its CREATED event through the real handler —
     * the exact path a webserver/scheduler submission takes.
     */
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

    /**
     * Stub the harness resolver spy the way the EE resolver will answer for this flow: its
     * scoped limits in evaluation order.
     */
    private void stubLimits(FlowWithSource flow, ScopedConcurrencyLimit... limits) {
        Mockito.doReturn(List.of(limits))
            .when(harness.concurrencyLimitResolver())
            .resolveLimits(Mockito.argThat(candidate -> candidate != null && flow.getId().equals(candidate.getId())));
    }

    private static FlowWithSource flowInNamespace(String namespace, String id, Concurrency concurrency) {
        return Flows.of(
            Flows.builder(logTask())
                .namespace(namespace)
                .id(id)
                .concurrency(concurrency)
                .build()
        );
    }

    private static Concurrency queue(int limit) {
        return limit(limit, Concurrency.Behavior.QUEUE);
    }

    private static Concurrency limit(int limit, Concurrency.Behavior behavior) {
        return Concurrency.builder().behavior(behavior).limit(limit).build();
    }

    private static Log logTask() {
        return Log.builder().id("log").type(Log.class.getName()).message("hello").build();
    }
}
