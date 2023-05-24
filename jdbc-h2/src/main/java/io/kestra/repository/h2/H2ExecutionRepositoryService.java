package io.kestra.repository.h2;

import io.kestra.core.models.executions.Execution;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class H2ExecutionRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<Execution> jdbcRepository, String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(List.of("fulltext"), query));
        }

        if (labels != null) {
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
}
