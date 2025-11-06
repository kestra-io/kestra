package io.kestra.repository.h2;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class H2ExecutionRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<Execution> jdbcRepository, String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(List.of("fulltext"), query));
        }

        if (labels != null) {
            labels.forEach((key, value) -> {
                Field<String> valueField = DSL.field("JQ_STRING(\"value\", '.labels[]? | select(.key == \"" + key + "\") | .value')", String.class);
                if (value == null) {
                    conditions.add(valueField.isNull());
                } else {
                    conditions.add(valueField.eq(value));
                }
            });
        }

        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }

    public static Condition findLabelCondition(Either<Map<?, ?>, String> input, QueryFilter.Op operation) {
        List<Condition> conditions = new ArrayList<>();
        List<Condition> inConditions = new ArrayList<>();
        if (input.isRight()) {
            var query = input.right().get();
            Field<String> keyField = DSL.field("JQ_STRING(\"value\", '.labels[]? | .key')", String.class);
            Field<String> valueField = DSL.field("JQ_STRING(\"value\", '.labels[]? | .value')", String.class);
            if (Objects.requireNonNull(operation) == QueryFilter.Op.CONTAINS) {
                conditions.add(keyField.contains(query).or(valueField.contains(query)));
            } else {
                throw new UnsupportedOperationException("Unsupported operation for query: " + operation);
            }
        } else {
            var labels = input.left().get();
            labels.forEach((key, value) -> {
                Field<String> valueField = DSL.field("JQ_STRING(\"value\", '.labels[]? | select(.key == \"" + key + "\") | .value')", String.class);
                switch (operation) {
                    case EQUALS -> conditions.add(value == null ? valueField.isNull() : valueField.eq((String) value));
                    case NOT_EQUALS, NOT_IN ->
                        conditions.add(value == null ? valueField.isNotNull() : valueField.isNull().or(valueField.ne((String) value)));
                    case IN -> inConditions.add(value == null ? valueField.isNull() : valueField.eq((String) value));
                    default -> throw new UnsupportedOperationException("Unsupported operation: " + operation);
                }
            });
        }

        if (!inConditions.isEmpty()) {
            conditions.add(DSL.or(inConditions));
        }
        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }
}
