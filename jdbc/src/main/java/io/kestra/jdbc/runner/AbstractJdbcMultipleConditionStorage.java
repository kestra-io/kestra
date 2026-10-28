package io.kestra.jdbc.runner;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.jooq.Condition;
import org.jooq.Select;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.repository.AbstractJdbcRepository;

public abstract class AbstractJdbcMultipleConditionStorage extends AbstractJdbcRepository implements MultipleConditionStorageInterface {
    protected io.kestra.jdbc.AbstractJdbcRepository<MultipleConditionWindow> jdbcRepository;

    public AbstractJdbcMultipleConditionStorage(io.kestra.jdbc.AbstractJdbcRepository<MultipleConditionWindow> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public Execution process(FlowId flow, List<MultipleCondition> multipleConditions, Map<String, Object> outputs, BiFunction<MultipleConditionStorageInterface, List<MultipleConditionWindow>, Execution> consumer) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext dslContext = DSL.using(configuration);

                // always lock in the same order to avoid deadlocks between concurrent evaluations
                List<MultipleConditionWindow> windows = multipleConditions.stream()
                    .sorted(Comparator.comparing(MultipleCondition::getId))
                    .map(multipleCondition ->
                    {
                        MultipleConditionWindow window = this.jdbcRepository.getOrInsert(
                            dslContext,
                            () -> get(dslContext, flow, multipleCondition.getId(), true),
                            () -> create(flow, multipleCondition, outputs)
                        );
                        return window.isValid(ZonedDateTime.now()) ? window : create(flow, multipleCondition, outputs);
                    })
                    .toList();

                return consumer.apply(new TransactionalStorage(dslContext), windows);
            });
    }

    @Override
    public Optional<MultipleConditionWindow> get(FlowId flow, String conditionId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> get(DSL.using(configuration), flow, conditionId, false));
    }

    private Optional<MultipleConditionWindow> get(DSLContext dslContext, FlowId flow, String conditionId, boolean forUpdate) {
        SelectConditionStep<Record1<Object>> select = dslContext
            .select(VALUE_FIELD)
            .from(this.jdbcRepository.getTable())
            .where(
                field("namespace").eq(flow.getNamespace())
                    .and(buildTenantCondition(flow.getTenantId()))
                    .and(field("flow_id").eq(flow.getId()))
                    .and(field("condition_id").eq(conditionId))
            );

        Select<Record1<Object>> query = forUpdate ? select.forUpdate() : select;
        return this.jdbcRepository.fetchOne(query);
    }

    @Override
    public List<MultipleConditionWindow> expired(String tenantId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(VALUE_FIELD)
                    .from(this.jdbcRepository.getTable())
                    .where(
                        getEndDataCondition().and(buildTenantCondition(tenantId))
                    );

                return this.jdbcRepository.fetch(select);
            });
    }

    protected Condition getEndDataCondition() {
        return field("end_date").lt(Timestamp.from(Instant.now()));
    }

    @Override
    public synchronized void save(List<MultipleConditionWindow> multipleConditionWindows) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                multipleConditionWindows
                    .forEach(window ->
                    {
                        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(window);
                        this.jdbcRepository.persist(window, context, fields);
                    });
            });
    }

    @Override
    public void delete(MultipleConditionWindow multipleConditionWindow) {
        this.jdbcRepository.delete(multipleConditionWindow);
    }

    /**
     * A view of this storage bound to the transaction of {@link #process}, so reads see the writes done inside it.
     */
    private class TransactionalStorage implements MultipleConditionStorageInterface {
        private final DSLContext dslContext;

        private TransactionalStorage(DSLContext dslContext) {
            this.dslContext = dslContext;
        }

        @Override
        public Execution process(FlowId flow, List<MultipleCondition> multipleConditions, Map<String, Object> outputs, BiFunction<MultipleConditionStorageInterface, List<MultipleConditionWindow>, Execution> consumer) {
            throw new UnsupportedOperationException("A multiple condition transaction cannot be nested.");
        }

        @Override
        public Optional<MultipleConditionWindow> get(FlowId flow, String conditionId) {
            return AbstractJdbcMultipleConditionStorage.this.get(dslContext, flow, conditionId, false);
        }

        @Override
        public List<MultipleConditionWindow> expired(String tenantId) {
            return AbstractJdbcMultipleConditionStorage.this.expired(tenantId);
        }

        @Override
        public void save(List<MultipleConditionWindow> multipleConditionWindows) {
            multipleConditionWindows.forEach(window -> jdbcRepository.persist(window, dslContext, jdbcRepository.persistFields(window)));
        }

        @Override
        public void delete(MultipleConditionWindow multipleConditionWindow) {
            jdbcRepository.delete(dslContext, multipleConditionWindow);
        }
    }
}
