package io.kestra.jdbc.repository;

import io.kestra.core.models.flows.Flow;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.kestra.jdbc.repository.AbstractJdbcRepository.field;

public abstract class JdbcFlowRepositoryService {
    public static Table<Record> lastRevision(AbstractJdbcRepository<Flow> jdbcRepository, boolean asterisk) {
        List<SelectFieldOrAsterisk> fields = new ArrayList<>();
        if (asterisk) {
            fields.add(DSL.asterisk()); //FIXME it didn't work on mySQL
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
            .transactionResult(configuration -> {
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

    public static Condition findCondition(AbstractJdbcRepository<Flow> jdbcRepository, String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(List.of("fulltext"), query));
        }

        if (labels != null)  {
            labels.forEach((key, value) -> {
                Field<String> field = DSL.field("JQ_STRING(\"value\", '.labels." + key + "')", String.class);

                if (value == null) {
                    conditions.add(field.isNotNull());
                } else {
                    conditions.add(field.eq(value));
                }
            });
        }

        return conditions.size() == 0 ? DSL.trueCondition() : DSL.and(conditions);
    }

    public static Condition findSourceCodeCondition(AbstractJdbcRepository<Flow> jdbcRepository, String query) {
        return jdbcRepository.fullTextCondition(List.of("source_code"), query);
    }
}
