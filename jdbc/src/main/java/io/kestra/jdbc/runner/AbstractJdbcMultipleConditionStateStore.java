package io.kestra.jdbc.runner;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.multipleflows.MultipleCondition;
import io.kestra.core.runners.TransactionContext;
import org.jooq.*;
import org.jooq.impl.DSL;

import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStateStore;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.repository.AbstractJdbcRepository;

public abstract class AbstractJdbcMultipleConditionStateStore extends AbstractJdbcRepository implements MultipleConditionStateStore {
    protected io.kestra.jdbc.AbstractJdbcRepository<MultipleConditionWindow> jdbcRepository;

    public AbstractJdbcMultipleConditionStateStore(io.kestra.jdbc.AbstractJdbcRepository<MultipleConditionWindow> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public Execution process(FlowId flow, MultipleCondition multipleCondition, Map<String, Object> outputs, BiFunction<TransactionContext, MultipleConditionWindow, Execution> consumer) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext dslContext = DSL.using(configuration);

                var multipleConditionWindow = this.jdbcRepository.getOrInsert(
                    dslContext,
                    () -> get(dslContext, flow, multipleCondition.getId()),
                    () -> create(flow, multipleCondition, outputs)
                );

                var txContext = new JdbcTransactionContext(dslContext);
                Execution newExecution = consumer.apply(txContext, multipleConditionWindow);

                if (newExecution != null && !Boolean.FALSE.equals(multipleCondition.getResetOnSuccess())) {
                    // re-fetch within the same transaction to get the state after the consumer's save(),
                    // then delete if all conditions are now satisfied
                    get(dslContext, flow, multipleCondition.getId())
                        .filter(multipleCondition::isConditionSatisfied)
                        .ifPresent(w -> delete(dslContext, w));
                }

                return newExecution;
            });
    }

    @Override
    public Optional<MultipleConditionWindow> get(FlowId flow, String conditionId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext dslContext = DSL.using(configuration);
                return this.get(dslContext, flow, conditionId);
            });
    }

    private Optional<MultipleConditionWindow> get(DSLContext dslContext, FlowId flow, String conditionId) {
        var select = dslContext
            .select(VALUE_FIELD)
            .from(this.jdbcRepository.getTable())
            .where(
                field("namespace").eq(flow.getNamespace())
                    .and(buildTenantCondition(flow.getTenantId()))
                    .and(field("flow_id").eq(flow.getId()))
                    .and(field("condition_id").eq(conditionId))
            )
            .forUpdate();

        return this.jdbcRepository.fetchOne(select);
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
    public void save(TransactionContext txContext, MultipleConditionWindow multipleConditionWindow) {
        var dslContext = txContext.unwrap(JdbcTransactionContext.class).getDslContext();
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(multipleConditionWindow);
        this.jdbcRepository.persist(multipleConditionWindow, dslContext, fields);
    }

    @Override
    public void save(MultipleConditionWindow multipleConditionWindow) {
        this.jdbcRepository.getDslContextWrapper().transaction( configuration -> {
            DSLContext dslContext = DSL.using(configuration);
            save(new JdbcTransactionContext(dslContext), multipleConditionWindow);
        });
    }

    @Override
    public void delete(MultipleConditionWindow multipleConditionWindow) {
        this.jdbcRepository.delete(multipleConditionWindow);
    }

    private void delete(DSLContext dslContext, MultipleConditionWindow multipleConditionWindow) {
        this.jdbcRepository.delete(dslContext, multipleConditionWindow);
    }
}
