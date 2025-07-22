package io.kestra.repository.h2;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class H2FlowRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, String query, Map<String, String> labels) {
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

    public static Condition findSourceCodeCondition(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, String query) {
        return jdbcRepository.fullTextCondition(List.of("source_code"), query);
    }

    public static Condition findCondition(Object labels, QueryFilter.Op operation) {
        List<Condition> conditions = new ArrayList<>();

        if (labels instanceof Map<?, ?> labelValues) {
            labelValues.forEach((key, value) -> {
                Field<String> valueField = DSL.field("JQ_STRING(\"value\", '.labels[]? | select(.key == \"" + key + "\") | .value')", String.class);
                Condition condition = switch (operation) {
                    case EQUALS -> value == null ? valueField.isNull() : valueField.eq((String) value);
                    case NOT_EQUALS -> value == null ? valueField.isNotNull() : valueField.ne((String) value);
                    default -> throw new UnsupportedOperationException("Unsupported operation: " + operation);
                };

                conditions.add(condition);
            });

        }
        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }
}
