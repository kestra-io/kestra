package io.kestra.jdbc.runner;

import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.Map;
import java.util.function.Consumer;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.runners.ExecutionDelay;
import io.kestra.executor.ExecutionDelayStateStore;
import io.kestra.jdbc.repository.AbstractJdbcRepository;

public abstract class AbstractJdbcExecutionDelayStateStore extends AbstractJdbcRepository implements ExecutionDelayStateStore {
    protected io.kestra.jdbc.AbstractJdbcRepository<ExecutionDelay> jdbcRepository;

    private static final Field<Object> DATE_FIELD = DSL.field(DSL.quotedName("date"));

    public AbstractJdbcExecutionDelayStateStore(io.kestra.jdbc.AbstractJdbcRepository<ExecutionDelay> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public void processExpired(Instant now, Consumer<ExecutionDelay> consumer) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration ->
            {
                var select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(DATE_FIELD.lessOrEqual(getNow(now)))
                    .forUpdate()
                    .skipLocked();

                this.jdbcRepository.fetch(select)
                    .forEach(executionDelay ->
                    {
                        consumer.accept(executionDelay);
                        jdbcRepository.delete(executionDelay);
                    });
            });
    }

    protected Temporal getNow(Instant now) {
        return now;
    }

    @Override
    public void save(ExecutionDelay executionDelay) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(executionDelay);
        this.jdbcRepository.persist(executionDelay, fields);
    }

    @Override
    public boolean saveUnlessProcessing(ExecutionDelay executionDelay) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);
                String key = this.jdbcRepository.key(executionDelay);

                // taking the row lock before the upsert keeps the lock order of processExpired (delay row, then execution row)
                boolean locked = context.fetchExists(
                    context.select(KEY_FIELD).from(this.jdbcRepository.getTable()).where(KEY_FIELD.eq(key)).forUpdate().skipLocked()
                );
                if (!locked && context.fetchExists(this.jdbcRepository.getTable(), KEY_FIELD.eq(key))) {
                    return false;
                }

                this.jdbcRepository.persist(executionDelay, context, null);
                return true;
            });
    }
}
