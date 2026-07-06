package io.kestra.executor.testkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.apache.commons.lang3.tuple.Pair;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.runners.ExecutionRunning;
import io.kestra.core.runners.TransactionContext;
import io.kestra.executor.ConcurrencyLimitStateStore;

/**
 * Map-backed {@link ConcurrencyLimitStateStore}: a running counter per flow uid.
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
            executionQueuedStateStore.pop(NoopTransactionContext.INSTANCE, flow.getTenantId(), flow.getNamespace(), flow.getId(), consumer);
        }
    }

    /**
     * The current running count for a flow (0 if never incremented).
     */
    public int running(FlowInterface flow) {
        return running(limits.get(uid(flow)));
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

    private static String uid(FlowInterface flow) {
        return flow.getTenantId() + "|" + flow.getNamespace() + "|" + flow.getId();
    }
}
