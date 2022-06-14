package io.kestra.jdbc.repository;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.jdbc.runner.JdbcIndexerInterface;
import jakarta.inject.Singleton;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public abstract class AbstractJdbcTriggerRepository extends AbstractJdbcRepository implements TriggerRepositoryInterface, JdbcIndexerInterface<Trigger> {
    protected io.kestra.jdbc.AbstractJdbcRepository<Trigger> jdbcRepository;

    public AbstractJdbcTriggerRepository(io.kestra.jdbc.AbstractJdbcRepository<Trigger> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public Optional<Trigger> findLast(TriggerContext trigger) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(field("key").eq(trigger.uid()));

                return this.jdbcRepository.fetchOne(select);
            });
    }

    @Override
    public Optional<Trigger> findByExecution(Execution execution) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        field("execution_id").eq(execution.getId())
                    );

                return this.jdbcRepository.fetchOne(select);
            });
    }

    @Override
    public List<Trigger> findAll() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectJoinStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable());

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public Trigger save(Trigger trigger) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(trigger);
        this.jdbcRepository.persist(trigger, fields);

        return trigger;
    }

    @Override
    public Trigger save(DSLContext dslContext, Trigger trigger) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(trigger);
        this.jdbcRepository.persist(trigger, dslContext, fields);

        return trigger;
    }
}
