package io.kestra.jdbc.runner;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;
import org.jooq.*;
import org.jooq.impl.DSL;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.runners.ExecutionRunning;
import io.kestra.core.runners.ScopedConcurrencyLimit;
import io.kestra.core.runners.TransactionContext;
import io.kestra.executor.ConcurrencyLimitStateStore;
import io.kestra.jdbc.repository.AbstractJdbcRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AbstractJdbcConcurrencyLimitStateStore extends AbstractJdbcRepository implements ConcurrencyLimitStateStore {
    public static final Field<Object> NAMESPACE_FIELD = field("namespace");
    public static final Field<Object> FLOW_ID_FIELD = field("flow_id");
    /** Blocked-candidate scan bound per release; remaining candidates wait for later releases. */
    static final int MAX_POP_CANDIDATES = 100;
    protected io.kestra.jdbc.AbstractJdbcRepository<ConcurrencyLimit> jdbcRepository;

    public AbstractJdbcConcurrencyLimitStateStore(io.kestra.jdbc.AbstractJdbcRepository<ConcurrencyLimit> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    /**
     * Fetch the concurrency limit counter, then process the count using the consumer function.
     * It locked the raw and is wrapped in a transaction, so the consumer should use the provided dslContext for any database access.
     * <p>
     * Note that to avoid a race when no concurrency limit counter exists, it first always tries to insert a 0 counter.
     */
    @Override
    public ExecutionRunning countThenProcess(FlowInterface flow, BiFunction<TransactionContext, ConcurrencyLimit, Pair<ExecutionRunning, ConcurrencyLimit>> consumer) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var dslContext = DSL.using(configuration);

                var selected = this.jdbcRepository.getOrInsert(
                    dslContext,
                    () -> fetchOne(dslContext, flow),
                    () -> ConcurrencyLimit.builder()
                        .tenantId(flow.getTenantId())
                        .namespace(flow.getNamespace())
                        .flowId(flow.getId())
                        .running(0)
                        .build()
                );

                var txContext = new JdbcTransactionContext(dslContext);
                var pair = consumer.apply(txContext, selected);
                update(dslContext, pair.getRight());
                return pair.getLeft();
            });
    }

    /**
     * Decrement the concurrency limit counter.
     * Must only be called when a flow having concurrency limit ends.
     */
    @Override
    public int decrement(FlowInterface flow) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var dslContext = DSL.using(configuration);

                return fetchOne(dslContext, flow).map(
                    concurrencyLimit ->
                    {
                        int newLimit = concurrencyLimit.getRunning() == 0 ? 0 : concurrencyLimit.getRunning() - 1;
                        update(dslContext, concurrencyLimit.withRunning(newLimit));
                        return newLimit;
                    }
                ).orElse(0);
            });
    }

    @Override
    public void decrementAndPop(FlowInterface flow, ExecutionQueuedStateStore executionQueuedStateStore,
        BiConsumer<TransactionContext, Execution> consumer) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration ->
            {
                var dslContext = DSL.using(configuration);

                // Decrement the counter
                int newLimit = fetchOne(dslContext, flow).map(
                    concurrencyLimit ->
                    {
                        int decremented = concurrencyLimit.getRunning() == 0 ? 0 : concurrencyLimit.getRunning() - 1;
                        update(dslContext, concurrencyLimit.withRunning(decremented));
                        return decremented;
                    }
                ).orElse(0);

                // Only pop if we're below the limit
                if (newLimit < flow.getConcurrency().getLimit()) {
                    executionQueuedStateStore.pop(
                        new JdbcTransactionContext(dslContext),
                        flow.getTenantId(),
                        flow.getNamespace(),
                        flow.getId(),
                        (ctx, queued) ->
                        {
                            // Increment the counter for the newly running execution
                            increment(ctx, flow);
                            // Call the consumer
                            consumer.accept(ctx, queued);
                        }
                    );
                } else {
                    log.error(
                        "Concurrency limit reached for flow {}.{} after decrementing the execution running count. No new executions will be dequeued.", flow.getNamespace(), flow.getId()
                    );
                }
            });
    }

    /**
     * Increment the concurrency limit counter.
     * Must only be called when a queued execution is popped, other use cases must pass thought the standard process of creating an execution.
     */
    @Override
    public void increment(TransactionContext txContext, FlowInterface flow) {
        var dslContext = txContext.unwrap(JdbcTransactionContext.class).getDslContext();
        fetchOne(dslContext, flow).ifPresent(
            concurrencyLimit -> update(dslContext, concurrencyLimit.withRunning(concurrencyLimit.getRunning() + 1))
        );
    }

    /**
     * Returns all concurrency limits from the database
     */
    public List<ConcurrencyLimit> find(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(this.buildTenantCondition(tenantId));

                return this.jdbcRepository.fetch(select);
            });
    }

    /**
     * Update a concurrency limit
     * WARNING: this is inherently unsafe and must only be used for administration purpose
     */
    public ConcurrencyLimit update(ConcurrencyLimit concurrencyLimit) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(concurrencyLimit);
        this.jdbcRepository.persist(concurrencyLimit, fields);

        return concurrencyLimit;
    }

    /**
     * Atomically read every scope counter, decide, and — on claim — increment them all, in one
     * transaction. Counter rows are locked in uid order so every multi-scope transaction
     * acquires locks in the same global order (no deadlocks).
     */
    @Override
    public ExecutionRunning countThenProcess(FlowInterface flow, List<ScopedConcurrencyLimit> limits, BiFunction<TransactionContext, List<Integer>, Pair<ExecutionRunning, Boolean>> consumer) {
        if (limits.size() == 1 && limits.getFirst().scope() == ScopedConcurrencyLimit.Scope.FLOW) {
            // keep the battle-tested single-row path of the interface default
            return ConcurrencyLimitStateStore.super.countThenProcess(flow, limits, consumer);
        }

        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var dslContext = DSL.using(configuration);

                Map<String, ConcurrencyLimit> rows = lockScopes(dslContext, limits);
                List<Integer> counts = limits.stream().map(scope -> running(rows.get(scope.uid()))).toList();

                var txContext = new JdbcTransactionContext(dslContext);
                var pair = consumer.apply(txContext, counts);
                if (Boolean.TRUE.equals(pair.getRight())) {
                    rows.values().forEach(row -> update(dslContext, row.withRunning(running(row) + 1)));
                }
                return pair.getLeft();
            });
    }

    /**
     * Multi-scope release: decrement every scope, then scan the queued candidates within the
     * widest freed scope, oldest first, and pop the first one that fits all of its own scopes.
     * <p>
     * The release and each candidate attempt run in separate transactions: a candidate attempt
     * first locks its queued row (SKIP LOCKED — never waits), then counter rows in uid order,
     * so lock acquisition stays globally ordered and deadlock-free. The price is that the
     * decrement and the pop are not atomic for cross-flow candidates: a crash in between
     * leaves the queued execution waiting for the next release of one of its scopes.
     */
    @Override
    public Optional<Execution> releaseThenPop(
        FlowInterface flow,
        List<ScopedConcurrencyLimit> limits,
        ExecutionQueuedStateStore executionQueuedStateStore,
        Function<Execution, List<ScopedConcurrencyLimit>> candidateLimits,
        BiFunction<TransactionContext, Execution, Execution> consumer) {
        if (limits.size() == 1 && limits.getFirst().scope() == ScopedConcurrencyLimit.Scope.FLOW) {
            // keep the battle-tested atomic decrementAndPop path of the interface default
            return ConcurrencyLimitStateStore.super.releaseThenPop(flow, limits, executionQueuedStateStore, candidateLimits, consumer);
        }

        // the JDBC executor always pairs the JDBC stores; the candidate scan/delete operations
        // are JDBC-level and deliberately not part of the backend-agnostic interface
        var queuedStore = (AbstractJdbcExecutionQueuedStateStore) executionQueuedStateStore;

        // release every scope
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration ->
            {
                var dslContext = DSL.using(configuration);
                sortedByUid(limits).forEach(
                    scope -> fetchOne(dslContext, scope)
                        .ifPresent(row -> update(dslContext, row.withRunning(Math.max(0, running(row) - 1))))
                );
            });

        // scan candidates, one attempt per transaction
        ScopedConcurrencyLimit widest = widestScope(limits);
        String namespace = widest.scope() == ScopedConcurrencyLimit.Scope.TENANT ? null : widest.namespace();
        String flowId = widest.scope() == ScopedConcurrencyLimit.Scope.FLOW ? widest.flowId() : null;

        Set<String> unfit = new HashSet<>();
        for (int attempt = 0; attempt < MAX_POP_CANDIDATES; attempt++) {
            Attempt result = this.jdbcRepository
                .getDslContextWrapper()
                .transactionResult(configuration ->
                {
                    var dslContext = DSL.using(configuration);
                    var txContext = new JdbcTransactionContext(dslContext);

                    Optional<ExecutionQueued> candidate = queuedStore.lockNextCandidate(txContext, flow.getTenantId(), namespace, flowId, unfit);
                    if (candidate.isEmpty()) {
                        return new Attempt(null, null);
                    }

                    List<ScopedConcurrencyLimit> candidateScopes = candidateLimits.apply(candidate.get().getExecution());
                    Map<String, ConcurrencyLimit> rows = lockScopes(dslContext, candidateScopes);
                    boolean fits = candidateScopes.stream().allMatch(scope -> running(rows.get(scope.uid())) < scope.concurrency().getLimit());
                    if (!fits) {
                        // a blocked candidate is skipped, not head-of-line blocking: it is
                        // reconsidered whenever one of its own scopes frees a slot
                        return new Attempt(null, candidate.get().uid());
                    }

                    rows.values().forEach(row -> update(dslContext, row.withRunning(running(row) + 1)));
                    queuedStore.delete(txContext, candidate.get());
                    return new Attempt(consumer.apply(txContext, candidate.get().getExecution()), null);
                });

            if (result.popped() != null) {
                return Optional.of(result.popped());
            }
            if (result.unfitUid() == null) {
                return Optional.empty();
            }
            unfit.add(result.unfitUid());
        }

        log.warn(
            "Stopped scanning queued executions after {} blocked candidates while releasing the concurrency slots of flow {}.{}; remaining candidates will be reconsidered on later releases.",
            MAX_POP_CANDIDATES, flow.getNamespace(), flow.getId()
        );
        return Optional.empty();
    }

    private record Attempt(Execution popped, String unfitUid) {
    }

    /**
     * Lock (or create at zero) the counter row of every scope, in uid order — the global lock
     * order shared by every multi-scope transaction.
     */
    private Map<String, ConcurrencyLimit> lockScopes(DSLContext dslContext, List<ScopedConcurrencyLimit> scopes) {
        Map<String, ConcurrencyLimit> rows = new HashMap<>();
        sortedByUid(scopes).forEach(
            scope -> rows.put(
                scope.uid(),
                this.jdbcRepository.getOrInsert(dslContext, () -> fetchOne(dslContext, scope), () -> zero(scope))
            )
        );
        return rows;
    }

    private static List<ScopedConcurrencyLimit> sortedByUid(List<ScopedConcurrencyLimit> scopes) {
        return scopes.stream().sorted(Comparator.comparing(ScopedConcurrencyLimit::uid)).toList();
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

    private static ConcurrencyLimit zero(ScopedConcurrencyLimit scope) {
        return ConcurrencyLimit.builder()
            .tenantId(scope.tenantId())
            .namespace(Objects.requireNonNullElse(scope.namespace(), ""))
            .flowId(Objects.requireNonNullElse(scope.flowId(), ""))
            .running(0)
            .build();
    }

    private Optional<ConcurrencyLimit> fetchOne(DSLContext dslContext, ScopedConcurrencyLimit scope) {
        var select = dslContext
            .select()
            .from(this.jdbcRepository.getTable())
            .where(this.buildTenantCondition(scope.tenantId()))
            .and(NAMESPACE_FIELD.eq(Objects.requireNonNullElse(scope.namespace(), "")))
            .and(FLOW_ID_FIELD.eq(Objects.requireNonNullElse(scope.flowId(), "")));

        return this.jdbcRepository.fetchOne(select.forUpdate());
    }

    private Optional<ConcurrencyLimit> fetchOne(DSLContext dslContext, FlowInterface flow) {
        var select = dslContext
            .select()
            .from(this.jdbcRepository.getTable())
            .where(this.buildTenantCondition(flow.getTenantId()))
            .and(NAMESPACE_FIELD.eq(flow.getNamespace()))
            .and(FLOW_ID_FIELD.eq(flow.getId()));

        return this.jdbcRepository.fetchOne(select.forUpdate());
    }

    private void update(DSLContext dslContext, ConcurrencyLimit concurrencyLimit) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(concurrencyLimit);
        this.jdbcRepository.persist(concurrencyLimit, dslContext, fields);
    }

    public Optional<ConcurrencyLimit> findById(String tenantId, String namespace, String flowId) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(this.buildTenantCondition(tenantId))
                    .and(NAMESPACE_FIELD.eq(namespace))
                    .and(FLOW_ID_FIELD.eq(flowId));
                return this.jdbcRepository.fetchOne(select);
            });
    }
}
