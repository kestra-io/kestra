package io.kestra.jdbc.repository;

import java.util.ArrayList;
import java.util.List;

import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import io.kestra.core.models.flows.FlowInterface;
import io.kestra.jdbc.AbstractJdbcRepository;

import static io.kestra.jdbc.repository.AbstractJdbcRepository.field;

public abstract class JdbcFlowRepositoryService {
    public static Table<Record> lastRevision(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, boolean asterisk) {
        List<SelectFieldOrAsterisk> fields = new ArrayList<>();
        if (asterisk) {
            // There is an issue in jOOQ with MySQL due to some limitations on MySQL.
            // So we need to qualify the asterisk see https://github.com/jOOQ/jOOQ/issues/15975.
            fields.add(jdbcRepository.getTable().asterisk());
        } else {
            fields.add(field("key", String.class));
            fields.add(field("revision", Integer.class));
        }

        fields.add(
            DSL.rowNumber()
                .over()
                .partitionBy(List.of(field("tenant_id"), field("namespace"), field("id")))
                .orderBy(field("revision").desc())
                .as("revision_rows")
        );

        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration ->
            {
                DSLContext context = DSL.using(configuration);

                return context.select(DSL.asterisk())
                    .from(
                        context.select(fields)
                            .from(jdbcRepository.getTable())
                            .asTable("rev_ord")
                    )
                    .where(field("revision_rows").eq(1))
                    .asTable("rev");
            });
    }

}
