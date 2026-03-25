package io.kestra.jdbc.runner;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.apache.commons.lang3.tuple.Pair;
import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.runners.ExecutionRunning;
import io.kestra.core.runners.TransactionContext;
import io.kestra.executor.ConcurrencyLimitStateStore;
import io.kestra.jdbc.repository.AbstractJdbcRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AbstractJdbcConcurrencyLimitStateStore extends AbstractJdbcRepository implements ConcurrencyLimitStateStore {
    public static final Field<Object> NAMESPACE_FIELD = field("namespace");
    public static final Field<Object> FLOW_ID_FIELD = field("flow_id");
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

                // Note: ideally, we should emit an INSERT IGNORE or ON CONFLICT DO NOTHING but H2 didn't support it.
                // So to avoid the case where no concurrency limit exist and two executors starts a flow concurrently, we select/insert and if the insert fail select again
                // Anyway this would only occur once in a flow lifecycle so even if it's not elegant it should work
                // But as this pattern didn't work with Postgres, we emit INSERT IGNORE in postgres so we're sure it works their also.
                var selected = fetchOne(dslContext, flow).orElseGet(() ->
                {
                    try {
                        var zeroConcurrencyLimit = ConcurrencyLimit.builder()
                            .tenantId(flow.getTenantId())
                            .namespace(flow.getNamespace())
                            .flowId(flow.getId())
                            .running(0)
                            .build();

                        Map<Field<Object>, Object> finalFields = this.jdbcRepository.persistFields(zeroConcurrencyLimit);
                        var insert = dslContext
                            .insertInto(this.jdbcRepository.getTable())
                            .set(KEY_FIELD, this.jdbcRepository.key(zeroConcurrencyLimit))
                            .set(finalFields);
                        if (dslContext.configuration().dialect().supports(SQLDialect.POSTGRES)) {
                            insert.onDuplicateKeyIgnore().execute();
                        } else {
                            insert.execute();
                        }
                    } catch (DataAccessException e) {
                        // we ignore any constraint violation
                    }
                    // refetch to have a lock on it
                    // at this point we are sure the record is inserted so it should never throw
                    return fetchOne(dslContext, flow).orElseThrow();
                });

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
