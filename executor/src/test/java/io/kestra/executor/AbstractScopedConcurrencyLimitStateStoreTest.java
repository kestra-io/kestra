package io.kestra.executor;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.ScopedConcurrencyLimit;
import io.kestra.core.services.ConcurrencyLimitResolver;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-scope scenarios of the {@link ConcurrencyLimitStateStore} scoped operations — the
 * namespace/tenant concurrency-limit engine (kestra-ee#8660) — against a container-injected
 * store, extended by the JDBC backends. Exercises claim-all accounting, the cross-flow
 * widest-scope pop, the blocked-candidate skip, and scope isolation.
 */
@MicronautTest(transactional = false)
public abstract class AbstractScopedConcurrencyLimitStateStoreTest {

    @Inject
    protected ConcurrencyLimitStateStore store;

    @Inject
    protected ExecutionQueuedStateStore queuedStore;

    @Inject
    protected MetricRegistry metricRegistry;

    /** flow uid → the scoped limits of that flow, used as the candidate resolver in pops. */
    private final Map<String, List<ScopedConcurrencyLimit>> limitsByFlow = new HashMap<>();

    @Test
    void shouldClaimEverySlotOnlyWhenConsumerClaims() {
        // Given: a flow with flow + namespace + tenant limits (a dedicated tenant: the tenant
        // counter is shared store-wide and this test leaves its claim in place)
        String tenant = IdUtils.create().toLowerCase();
        String namespace = "io.kestra." + IdUtils.create().toLowerCase();
        Flow flow = flow(tenant, namespace, queue(5));
        List<ScopedConcurrencyLimit> limits = register(
            flow,
            ScopedConcurrencyLimit.ofFlow(flow),
            ScopedConcurrencyLimit.ofNamespace(tenant, namespace, queue(5)),
            ScopedConcurrencyLimit.ofTenant(tenant, queue(5))
        );

        // When: one claim, then one decline
        store.countThenProcess(flow, limits, (txContext, counts) -> Pair.of(null, true));
        AtomicReference<List<Integer>> seen = new AtomicReference<>();
        store.countThenProcess(flow, limits, (txContext, counts) ->
        {
            seen.set(counts);
            return Pair.of(null, false);
        });

        // Then: the claim incremented every scope once, the decline none
        assertThat(seen.get()).containsExactly(1, 1, 1);
        assertThat(counts(flow, limits)).containsExactly(1, 1, 1);
    }

    @Test
    void shouldShareTheNamespaceCounterAcrossFlows() {
        // Given: two flows under the same limited namespace
        String namespace = "io.kestra." + IdUtils.create().toLowerCase();
        ScopedConcurrencyLimit namespaceScope = ScopedConcurrencyLimit.ofNamespace("main", namespace, queue(5));
        Flow flowA = flow(namespace, queue(5));
        Flow flowB = flow(namespace, null);
        List<ScopedConcurrencyLimit> limitsA = register(flowA, ScopedConcurrencyLimit.ofFlow(flowA), namespaceScope);
        List<ScopedConcurrencyLimit> limitsB = register(flowB, namespaceScope);

        // When: each flow claims once
        store.countThenProcess(flowA, limitsA, (txContext, counts) -> Pair.of(null, true));
        store.countThenProcess(flowB, limitsB, (txContext, counts) -> Pair.of(null, true));

        // Then: the namespace counter aggregates both, the flow counter only its own
        assertThat(counts(flowA, limitsA)).containsExactly(1, 2);
        assertThat(counts(flowB, limitsB)).containsExactly(2);
    }

    @Test
    void shouldReleaseEveryScopeAndPopACrossFlowCandidate() {
        // Given: a namespace limited to 1 shared by two flows; A runs, B is queued behind it
        String namespace = "io.kestra." + IdUtils.create().toLowerCase();
        ScopedConcurrencyLimit namespaceScope = ScopedConcurrencyLimit.ofNamespace("main", namespace, queue(1));
        Flow flowA = flow(namespace, null);
        Flow flowB = flow(namespace, null);
        List<ScopedConcurrencyLimit> limitsA = register(flowA, namespaceScope);
        register(flowB, namespaceScope);
        store.countThenProcess(flowA, limitsA, (txContext, counts) -> Pair.of(null, true));
        Execution waitingB = enqueue(flowB, Instant.now());

        // When: A terminates
        Optional<Execution> popped = store.releaseThenPop(flowA, limitsA, queuedStore, this::candidateLimits, (txContext, queued) -> queued);

        // Then: B — an execution of a different flow — takes over the namespace slot
        assertThat(popped).map(Execution::getId).contains(waitingB.getId());
        assertThat(counts(flowA, limitsA)).containsExactly(1);
    }

    @Test
    void shouldSkipCandidateBlockedByItsOwnFlowLimitAndPopTheNextFit() {
        // Given: a namespace limited to 2; flow A additionally limited to 1; A1 and C fill the
        // namespace; A2 queues first, then B
        String namespace = "io.kestra." + IdUtils.create().toLowerCase();
        ScopedConcurrencyLimit namespaceScope = ScopedConcurrencyLimit.ofNamespace("main", namespace, queue(2));
        Flow flowA = flow(namespace, queue(1));
        Flow flowB = flow(namespace, null);
        Flow flowC = flow(namespace, null);
        List<ScopedConcurrencyLimit> limitsA = register(flowA, ScopedConcurrencyLimit.ofFlow(flowA), namespaceScope);
        register(flowB, namespaceScope);
        List<ScopedConcurrencyLimit> limitsC = register(flowC, namespaceScope);
        store.countThenProcess(flowA, limitsA, (txContext, counts) -> Pair.of(null, true));
        store.countThenProcess(flowC, limitsC, (txContext, counts) -> Pair.of(null, true));
        Execution waitingA2 = enqueue(flowA, Instant.now().minusSeconds(60));
        Execution waitingB = enqueue(flowB, Instant.now());

        // When: C terminates, freeing one namespace slot
        Optional<Execution> popped = store.releaseThenPop(flowC, limitsC, queuedStore, this::candidateLimits, (txContext, queued) -> queued);

        // Then: A2 — the oldest candidate — is still blocked by its own flow limit (A1 runs),
        // so it is skipped and B pops instead of starving behind it
        assertThat(popped).map(Execution::getId).contains(waitingB.getId());

        // When: A1 terminates, freeing the flow A slot and a namespace slot
        Optional<Execution> reconsidered = store.releaseThenPop(flowA, limitsA, queuedStore, this::candidateLimits, (txContext, queued) -> queued);

        // Then: the skipped A2 is reconsidered and finally pops
        assertThat(reconsidered).map(Execution::getId).contains(waitingA2.getId());
        assertThat(counts(flowA, limitsA)).containsExactly(1, 2);
    }

    @Test
    void shouldNotPopCandidateOutsideTheFreedScopes() {
        // Given: two limited namespaces, each with one running and one queued execution;
        // the foreign candidate queued first (it is the oldest overall)
        String namespace1 = "io.kestra." + IdUtils.create().toLowerCase();
        String namespace2 = "io.kestra." + IdUtils.create().toLowerCase();
        ScopedConcurrencyLimit namespace1Scope = ScopedConcurrencyLimit.ofNamespace("main", namespace1, queue(1));
        ScopedConcurrencyLimit namespace2Scope = ScopedConcurrencyLimit.ofNamespace("main", namespace2, queue(1));
        Flow flowA = flow(namespace1, null);
        Flow flowB = flow(namespace2, null);
        List<ScopedConcurrencyLimit> limitsA = register(flowA, namespace1Scope);
        List<ScopedConcurrencyLimit> limitsB = register(flowB, namespace2Scope);
        store.countThenProcess(flowA, limitsA, (txContext, counts) -> Pair.of(null, true));
        store.countThenProcess(flowB, limitsB, (txContext, counts) -> Pair.of(null, true));
        Execution waitingB2 = enqueue(flowB, Instant.now().minusSeconds(60));
        Execution waitingA2 = enqueue(flowA, Instant.now());

        // When: A terminates, freeing only the first namespace's slot
        Optional<Execution> popped = store.releaseThenPop(flowA, limitsA, queuedStore, this::candidateLimits, (txContext, queued) -> queued);

        // Then: A2 pops even though B2 queued earlier — B2 shares no scope with the freed slot
        assertThat(popped).map(Execution::getId).contains(waitingA2.getId());
        assertThat(counts(flowB, limitsB)).containsExactly(1);
        assertThat(waitingStillQueued(waitingB2)).isTrue();
    }

    @Test
    void shouldReturnEmptyAndKeepCandidatesWhenNoneFits() {
        // Given: a namespace limited to 2; flow A limited to 1 holds a flow slot and a
        // namespace slot, another namespace slot is held by C; A2 is the only candidate
        String namespace = "io.kestra." + IdUtils.create().toLowerCase();
        ScopedConcurrencyLimit namespaceScope = ScopedConcurrencyLimit.ofNamespace("main", namespace, queue(2));
        Flow flowA = flow(namespace, queue(1));
        Flow flowC = flow(namespace, null);
        List<ScopedConcurrencyLimit> limitsA = register(flowA, ScopedConcurrencyLimit.ofFlow(flowA), namespaceScope);
        List<ScopedConcurrencyLimit> limitsC = register(flowC, namespaceScope);
        store.countThenProcess(flowA, limitsA, (txContext, counts) -> Pair.of(null, true));
        store.countThenProcess(flowC, limitsC, (txContext, counts) -> Pair.of(null, true));
        Execution waitingA2 = enqueue(flowA, Instant.now());

        // When: C terminates — A2 stays blocked by its own flow limit
        Optional<Execution> popped = store.releaseThenPop(flowC, limitsC, queuedStore, this::candidateLimits, (txContext, queued) -> queued);

        // Then: nothing pops and the candidate survives for later releases
        assertThat(popped).isEmpty();
        assertThat(waitingStillQueued(waitingA2)).isTrue();
    }

    @Test
    void shouldReleaseTheScopesTheExecutionWasAdmittedUnderWhenTheLimitWasRemovedMidRun() {
        // Given: an execution admitted under a tenant limit — it claimed the tenant slot
        // (a dedicated tenant: the release scans queued candidates tenant-wide)
        String tenant = IdUtils.create().toLowerCase();
        String namespace = "io.kestra." + IdUtils.create().toLowerCase();
        ScopedConcurrencyLimit tenantScope = ScopedConcurrencyLimit.ofTenant(tenant, queue(2));
        Flow flow = flow(tenant, namespace, null);
        store.countThenProcess(flow, List.of(tenantScope), (txContext, counts) -> Pair.of(null, true));
        assertThat(counts(flow, List.of(tenantScope))).containsExactly(1);

        // Given: the tenant limit is removed while the execution runs — the resolver now
        // finds nothing at termination
        ConcurrencyLimitResolver resolver = Mockito.mock(ConcurrencyLimitResolver.class);
        Mockito.when(resolver.resolveLimits(Mockito.any())).thenReturn(List.of());
        ConcurrencySlotReleaseProcessor processor = new ConcurrencySlotReleaseProcessor(
            store, resolver, queuedStore, Mockito.mock(FlowMetaStoreInterface.class), metricRegistry
        );

        // When: the execution terminates, carrying the claim stamp the gate persisted at
        // admission (ExecutionMetadata#concurrencyScopes)
        Execution terminated = Execution.newExecution(flow, List.of())
            .withState(State.Type.RUNNING)
            .withState(State.Type.SUCCESS);
        terminated = terminated.withMetadata(terminated.getMetadata().withConcurrencyScopes(List.of(tenantScope.uid())));
        Optional<Execution> popped = processor.release(new ExecutorContext(terminated, io.kestra.core.models.flows.FlowWithSource.of(flow, "")));

        // Then: the slot it was admitted under is released — the counter must not leak and
        // block the tenant until a manual reset
        assertThat(popped).isEmpty();
        assertThat(counts(flow, List.of(tenantScope))).containsExactly(0);
    }

    // --- fixtures

    private static Flow flow(String namespace, Concurrency concurrency) {
        return flow("main", namespace, concurrency);
    }

    private static Flow flow(String tenantId, String namespace, Concurrency concurrency) {
        return Flow.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .id(IdUtils.create())
            .revision(1)
            .concurrency(concurrency)
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("test").build()))
            .build();
    }

    private static Concurrency queue(int limit) {
        return Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(limit).build();
    }

    /** Remember a flow's scoped limits so {@link #candidateLimits} can resolve pop candidates. */
    private List<ScopedConcurrencyLimit> register(Flow flow, ScopedConcurrencyLimit... limits) {
        List<ScopedConcurrencyLimit> list = List.of(limits);
        limitsByFlow.put(flow.getId(), list);
        return list;
    }

    private List<ScopedConcurrencyLimit> candidateLimits(Execution execution) {
        return limitsByFlow.getOrDefault(execution.getFlowId(), List.of());
    }

    /** Save a queued execution the way the gate does: inside the counting transaction. */
    private Execution enqueue(Flow flow, Instant date) {
        Execution execution = Execution.newExecution(flow, List.of());
        store.countThenProcess(flow, (txContext, limit) ->
        {
            queuedStore.save(
                txContext,
                ExecutionQueued.builder()
                    .tenantId(flow.getTenantId())
                    .namespace(flow.getNamespace())
                    .flowId(flow.getId())
                    .execution(execution)
                    .date(date)
                    .build()
            );
            return Pair.of(null, limit);
        });
        return execution;
    }

    /** The running counts of the given scopes, in scope order, read without claiming. */
    private List<Integer> counts(Flow flow, List<ScopedConcurrencyLimit> limits) {
        AtomicReference<List<Integer>> counts = new AtomicReference<>();
        store.countThenProcess(flow, limits, (txContext, read) ->
        {
            counts.set(read);
            return Pair.of(null, false);
        });
        return counts.get();
    }

    /** Whether the execution is still queued — probed with a flow-keyed pop that re-queues. */
    private boolean waitingStillQueued(Execution execution) {
        Flow probe = Flow.builder()
            .tenantId(execution.getTenantId())
            .namespace(execution.getNamespace())
            .id(execution.getFlowId())
            .revision(1)
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("test").build()))
            .build();
        AtomicReference<Execution> seen = new AtomicReference<>();
        store.countThenProcess(probe, (txContext, limit) ->
        {
            queuedStore.pop(txContext, execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), (tx, popped) -> seen.set(popped));
            if (seen.get() != null) {
                queuedStore.save(
                    txContext,
                    ExecutionQueued.builder()
                        .tenantId(execution.getTenantId())
                        .namespace(execution.getNamespace())
                        .flowId(execution.getFlowId())
                        .execution(seen.get())
                        .date(Instant.now())
                        .build()
                );
            }
            return Pair.of(null, limit);
        });
        return seen.get() != null && seen.get().getId().equals(execution.getId());
    }
}
