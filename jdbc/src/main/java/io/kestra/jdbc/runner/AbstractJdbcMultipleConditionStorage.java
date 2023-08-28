package io.kestra.jdbc.runner;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractJdbcMultipleConditionStorage extends AbstractJdbcRepository implements MultipleConditionStorageInterface {
    protected io.kestra.jdbc.AbstractJdbcRepository<MultipleConditionWindow> jdbcRepository;

    public AbstractJdbcMultipleConditionStorage(io.kestra.jdbc.AbstractJdbcRepository<MultipleConditionWindow> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public Optional<MultipleConditionWindow> get(Flow flow, String conditionId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        field("namespace").eq(flow.getNamespace())
                            .and(field("flow_id").eq(flow.getId()))
                            .and(field("condition_id").eq(conditionId))
                    );

                return this.jdbcRepository.fetchOne(select);
            });
    }

    @Override
    public List<MultipleConditionWindow> expired() {
        ZonedDateTime now = ZonedDateTime.now();

        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        field("start_date").lt(now.toOffsetDateTime())
                            .and(field("end_date").lt(now.toOffsetDateTime()))
                    );

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public synchronized void save(List<MultipleConditionWindow> multipleConditionWindows) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSLContext context = DSL.using(configuration);

                multipleConditionWindows
                    .forEach(window -> {
                        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(window);
                        this.jdbcRepository.persist(window, context, fields);
                    });
            });
    }

    @Override
    public void delete(MultipleConditionWindow multipleConditionWindow) {
        this.jdbcRepository.delete(multipleConditionWindow);
    }
}
