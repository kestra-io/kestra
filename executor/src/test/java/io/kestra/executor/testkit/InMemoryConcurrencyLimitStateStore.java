package io.kestra.executor.testkit;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.runners.ExecutionRunning;
import io.kestra.core.runners.ScopedConcurrencyLimit;
import io.kestra.core.runners.TransactionContext;
import io.kestra.executor.ConcurrencyLimitStateStore;

/**
 * Map-backed {@link ConcurrencyLimitStateStore}: a running counter per scope uid — the flow uid
 * for flow-scoped limits, {@code tenant|namespace|} / {@code tenant||} for namespace and tenant
 * scoped ones. Unlike the JDBC/ES stores it fully implements the scoped operations, including
 * the widest-scope FIFO pop with per-candidate fit check.
 */
public class InMemoryConcurrencyLimitStateStore implements ConcurrencyLimitStateStore {
    private final Map<String, ConcurrencyLimit> limits = new ConcurrentHashMap<>();

    @Override
    public synchronized ExecutionRunning countThenProcess(FlowInterface flow, BiFunction<TransactionContext, ConcurrencyLimit, Pair<ExecutionRunning, ConcurrencyLimit>> consumer) {
        ConcurrencyLimit current = limits.computeIfAbsent(uid(flow), key -> emptyLimit(flow));
        Pair<ExecutionRunning, ConcurrencyLimit> result = consumer.apply(NoopTransactionContext.INSTANCE, current);
        limits.put(uid(flow), result.getRight());
        return result.getLeft();
    }

    @Override
    public synchronized int decrement(FlowInterface flow) {
        ConcurrencyLimit updated = limits.compute(
            uid(flow),
            (key, limit) -> (limit == null ? emptyLimit(flow) : limit).withRunning(Math.max(0, running(limit) - 1))
        );
        return updated.getRunning();
    }

    @Override
    public synchronized void increment(TransactionContext txContext, FlowInterface flow) {
        limits.compute(
            uid(flow),
            (key, limit) -> (limit == null ? emptyLimit(flow) : limit).withRunning(running(limit) + 1)
        );
    }

    @Override
    public synchronized void decrementAndPop(FlowInterface flow, ExecutionQueuedStateStore executionQueuedStateStore, BiConsumer<TransactionContext, Execution> consumer) {
        int running = decrement(flow);
        if (flow.getConcurrency() != null && running < flow.getConcurrency().getLimit()) {
            executionQueuedStateStore.pop(
                NoopTransactionContext.INSTANCE,
                flow.getTenantId(),
                flow.getNamespace(),
                flow.getId(),
                (txContext, queued) ->
                {
                    // the popped execution takes the freed slot: re-increment the counter before
                    // handing it to the consumer (AbstractJdbcConcurrencyLimitStateStore#decrementAndPop)
                    increment(txContext, flow);
                    consumer.accept(txContext, queued);
                }
            );
        }
    }

    @Override
    public synchronized ExecutionRunning countThenProcess(FlowInterface flow, List<ScopedConcurrencyLimit> scopes,
        BiFunction<TransactionContext, List<Integer>, Pair<ExecutionRunning, Boolean>> consumer) {
        List<Integer> counts = scopes.stream()
            .map(scope -> running(limits.computeIfAbsent(scope.uid(), key -> emptyLimit(scope))))
            .toList();
        Pair<ExecutionRunning, Boolean> result = consumer.apply(NoopTransactionContext.INSTANCE, counts);
        if (Boolean.TRUE.equals(result.getRight())) {
            scopes.forEach(this::increment);
        }
        return result.getLeft();
    }

    @Override
    public synchronized Optional<Execution> releaseThenPop(
        FlowInterface flow,
        List<ScopedConcurrencyLimit> scopes,
        ExecutionQueuedStateStore executionQueuedStateStore,
        Function<Execution, List<ScopedConcurrencyLimit>> candidateLimits,
        BiFunction<TransactionContext, Execution, Execution> consumer) {
        scopes.forEach(this::decrement);

        // Scan the queued candidates FIFO within the widest freed scope: candidates outside it
        // share no counter with the released execution, so their fit cannot have changed. The
        // fake needs the whole queued list, hence the kit queued store (a production
        // implementation would add a scan operation to its own queued storage instead).
        InMemoryExecutionQueuedStateStore queuedStore = (InMemoryExecutionQueuedStateStore) executionQueuedStateStore;
        ScopedConcurrencyLimit widest = widestScope(scopes);
        List<ExecutionQueued> candidates = queuedStore.queued().stream()
            .filter(queued -> covers(widest, queued.getTenantId(), queued.getNamespace(), queued.getFlowId()))
            .sorted(Comparator.comparing(ExecutionQueued::getDate))
            .toList();

        for (ExecutionQueued candidate : candidates) {
            List<ScopedConcurrencyLimit> candidateScopes = candidateLimits.apply(candidate.getExecution());
            boolean fits = candidateScopes.stream().allMatch(scope -> running(scope) < scope.concurrency().getLimit());
            if (fits) {
                candidateScopes.forEach(this::increment);
                queuedStore.remove(candidate.getExecution());
                return Optional.of(consumer.apply(NoopTransactionContext.INSTANCE, candidate.getExecution()));
            }
            // a blocked candidate is skipped, not head-of-line blocking: it is reconsidered
            // whenever one of its own scopes frees a slot
        }

        return Optional.empty();
    }

    /**
     * The current running count for a flow (0 if never incremented).
     */
    public int running(FlowInterface flow) {
        return running(limits.get(uid(flow)));
    }

    /**
     * The current running count for a scope (0 if never incremented).
     */
    public int running(ScopedConcurrencyLimit scope) {
        return running(limits.get(scope.uid()));
    }

    private synchronized void increment(ScopedConcurrencyLimit scope) {
        limits.compute(
            scope.uid(),
            (key, limit) -> (limit == null ? emptyLimit(scope) : limit).withRunning(running(limit) + 1)
        );
    }

    private synchronized void decrement(ScopedConcurrencyLimit scope) {
        limits.compute(
            scope.uid(),
            (key, limit) -> (limit == null ? emptyLimit(scope) : limit).withRunning(Math.max(0, running(limit) - 1))
        );
    }

    // mirrors the scope check the production stores express in SQL/queries (was ScopedConcurrencyLimit#covers)
    private static boolean covers(ScopedConcurrencyLimit limit, String tenantId, String namespace, String flowId) {
        if (!Objects.equals(limit.tenantId(), tenantId)) {
            return false;
        }
        return switch (limit.scope()) {
            case FLOW -> Objects.equals(limit.namespace(), namespace) && Objects.equals(limit.flowId(), flowId);
            case NAMESPACE -> Objects.equals(limit.namespace(), namespace) || (namespace != null && namespace.startsWith(limit.namespace() + "."));
            case TENANT -> true;
        };
    }

    private static ScopedConcurrencyLimit widestScope(List<ScopedConcurrencyLimit> scopes) {
        Optional<ScopedConcurrencyLimit> tenant = scopes.stream()
            .filter(scope -> scope.scope() == ScopedConcurrencyLimit.Scope.TENANT)
            .findFirst();
        if (tenant.isPresent()) {
            return tenant.get();
        }

        // namespace scopes are ancestors of the same flow namespace: the shortest is the widest
        return scopes.stream()
            .filter(scope -> scope.scope() == ScopedConcurrencyLimit.Scope.NAMESPACE)
            .min(Comparator.comparingInt(scope -> scope.namespace().length()))
            .orElse(scopes.getFirst());
    }

    private static int running(ConcurrencyLimit limit) {
        return limit == null || limit.getRunning() == null ? 0 : limit.getRunning();
    }

    private static ConcurrencyLimit emptyLimit(FlowInterface flow) {
        return ConcurrencyLimit.builder()
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .running(0)
            .build();
    }

    private static ConcurrencyLimit emptyLimit(ScopedConcurrencyLimit scope) {
        return ConcurrencyLimit.builder()
            .tenantId(scope.tenantId())
            .namespace(Objects.requireNonNullElse(scope.namespace(), ""))
            .flowId(Objects.requireNonNullElse(scope.flowId(), ""))
            .running(0)
            .build();
    }

    private static String uid(FlowInterface flow) {
        return flow.getTenantId() + "|" + flow.getNamespace() + "|" + flow.getId();
    }
}
