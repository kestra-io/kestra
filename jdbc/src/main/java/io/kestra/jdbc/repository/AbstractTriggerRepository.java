package io.kestra.jdbc.repository;

import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.jdbc.AbstractJdbcRepository;
import jakarta.inject.Singleton;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public abstract class AbstractTriggerRepository extends AbstractRepository implements TriggerRepositoryInterface {
    protected AbstractJdbcRepository<Trigger> jdbcRepository;

    public AbstractTriggerRepository(AbstractJdbcRepository<Trigger> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public Optional<Trigger> findLast(TriggerContext trigger) {
        return this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(DSL.field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        DSL.field("namespace").eq(trigger.getNamespace())
                            .and(DSL.field("flow_id").eq(trigger.getFlowId()))
                            .and(DSL.field("trigger_id").eq(trigger.getTriggerId()))
                    );

                return this.jdbcRepository.fetchOne(select);
            });
    }

    @Override
    public List<Trigger> findAll() {
        return this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                SelectJoinStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(DSL.field("value"))
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
}
