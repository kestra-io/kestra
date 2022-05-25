package io.kestra.runner.postgres;

import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.jdbc.runner.AbstractJdbcMultipleConditionStorage;
import io.kestra.repository.postgres.PostgresRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.time.ZonedDateTime;
import java.util.List;

@Singleton
@PostgresQueueEnabled
public class PostgresMultipleConditionStorage extends AbstractJdbcMultipleConditionStorage {
    public PostgresMultipleConditionStorage(ApplicationContext applicationContext) {
        super(new PostgresRepository<>(MultipleConditionWindow.class, applicationContext));
    }

    @Override
    public List<MultipleConditionWindow> expired() {
        ZonedDateTime now = ZonedDateTime.now();

        // bug on postgres with timestamp, use unix integer

        return this.jdbcRepository
            .getDslContext()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(DSL.field("value"))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        DSL.field("start_date").lessOrEqual((int) now.toInstant().getEpochSecond())
                            .and(DSL.field("end_date").lessOrEqual((int) now.toInstant().getEpochSecond()))
                    );

                return this.jdbcRepository.fetch(select);
            });
    }
}
