package io.kestra.jdbc.runner;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.kestra.jdbc.repository.AbstractRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractJdbcMultipleConditionStorage extends AbstractRepository implements MultipleConditionStorageInterface {
    protected AbstractJdbcRepository<MultipleConditionWindow> jdbcRepository;

    public AbstractJdbcMultipleConditionStorage(AbstractJdbcRepository<MultipleConditionWindow> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public Optional<MultipleConditionWindow> get(Flow flow, String conditionId) {
        return this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(DSL.field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        DSL.field("namespace").eq(flow.getNamespace())
                            .and(DSL.field("flow_id").eq(flow.getId()))
                            .and(DSL.field("condition_id").eq(conditionId))
                    );

                return this.jdbcRepository.fetchOne(select);
            });
    }

    @Override
    public List<MultipleConditionWindow> expired() {
        ZonedDateTime now = ZonedDateTime.now();

        return this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(DSL.field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        DSL.field("start_date").lt(now.toInstant())
                            .and(DSL.field("end_date").lt(now.toInstant()))
                    );

                return this.jdbcRepository.fetch(select);
            });
    }

    public synchronized void save(List<MultipleConditionWindow> multipleConditionWindows) {
        this.jdbcRepository
            .getDslContext()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);

                multipleConditionWindows
                    .forEach(window -> {
                        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(window);
                        this.jdbcRepository.persist(window, context, fields);
                    });
            });
    }

    public void delete(MultipleConditionWindow multipleConditionWindow) {
        this.jdbcRepository.delete(multipleConditionWindow);
    }
}
