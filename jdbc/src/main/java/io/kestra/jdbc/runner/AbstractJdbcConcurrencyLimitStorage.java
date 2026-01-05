package io.kestra.jdbc.runner;

import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.runners.ConcurrencyLimit;
import io.kestra.core.runners.ExecutionRunning;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public class AbstractJdbcConcurrencyLimitStorage extends AbstractJdbcRepository {
    protected io.kestra.jdbc.AbstractJdbcRepository<ConcurrencyLimit> jdbcRepository;

    public AbstractJdbcConcurrencyLimitStorage(io.kestra.jdbc.AbstractJdbcRepository<ConcurrencyLimit> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    /**
     * Fetch the concurrency limit counter, then process the count using the consumer function.
     * It locked the raw and is wrapped in a transaction, so the consumer should use the provided dslContext for any database access.
     * <p>
     * Note that to avoid a race when no concurrency limit counter exists, it first always tries to insert a 0 counter.
     */
    public ExecutionRunning countThenProcess(FlowInterface flow, BiFunction<DSLContext, ConcurrencyLimit, Pair<ExecutionRunning, ConcurrencyLimit>> consumer) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var dslContext = DSL.using(configuration);

                // Note: ideally, we should emit an INSERT IGNORE or ON CONFLICT DO NOTHING but H2 didn't support it.
                // So to avoid the case where no concurrency limit exist and two executors starts a flow concurrently, we select/insert and if the insert fail select again
                // Anyway this would only occur once in a flow lifecycle so even if it's not elegant it should work
                // But as this pattern didn't work with Postgres, we emit INSERT IGNORE in postgres so we're sure it works their also.
                var selected = fetchOne(dslContext, flow).orElseGet(() -> {
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
                            .set(field("key"), this.jdbcRepository.key(zeroConcurrencyLimit))
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

                var pair = consumer.apply(dslContext, selected);
                update(dslContext, pair.getRight());
                return pair.getLeft();
            });
    }

    /**
     * Decrement the concurrency limit counter.
     * Must only be called when a flow having concurrency limit ends.
     */
    public int decrement(FlowInterface flow) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var dslContext = DSL.using(configuration);

                return fetchOne(dslContext, flow).map(
                    concurrencyLimit -> {
                        int newLimit = concurrencyLimit.getRunning() == 0 ? 0 : concurrencyLimit.getRunning() - 1;
                        update(dslContext, concurrencyLimit.withRunning(newLimit));
                        return newLimit;
                    }
                ).orElse(0);
            });
    }

    /**
     * Increment the concurrency limit counter.
     * Must only be called when a queued execution is popped, other use cases must pass thought the standard process of creating an execution.
     */
    public void increment(DSLContext dslContext, FlowInterface flow) {
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
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(field("value"))
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
            .and(field("namespace").eq(flow.getNamespace()))
            .and(field("flow_id").eq(flow.getId()));

        return this.jdbcRepository.fetchOne(select.forUpdate());
    }

    private void update(DSLContext dslContext, ConcurrencyLimit concurrencyLimit) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(concurrencyLimit);
        this.jdbcRepository.persist(concurrencyLimit, dslContext, fields);
    }

    public Optional<ConcurrencyLimit> findById(String tenantId, String namespace, String flowId) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(this.buildTenantCondition(tenantId))
                    .and(field("namespace").eq(namespace))
                    .and(field("flow_id").eq(flowId));
                return this.jdbcRepository.fetchOne(select);
            });
    }
}
