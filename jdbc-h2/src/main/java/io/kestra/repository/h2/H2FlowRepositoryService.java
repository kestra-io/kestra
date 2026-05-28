package io.kestra.repository.h2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.jdbc.AbstractJdbcRepository;

public abstract class H2FlowRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(List.of("fulltext"), query));
        }

        if (labels != null) {
            labels.forEach((key, value) ->
            {
                Field<String> valueField = DSL.field("JQ_STRING(\"value\", CONCAT('.labels[]? | select(.key == \"', {0}, '\") | .value'))", String.class, DSL.val(key, String.class));
                if (value == null) {
                    conditions.add(valueField.isNull());
                } else {
                    conditions.add(valueField.eq(value));
                }
            });
        }

        return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
    }

    public static Condition findSourceCodeCondition(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, String query) {
        return jdbcRepository.fullTextCondition(List.of("source_code"), query);
    }

    /**
     * Builds a condition that matches flows containing at least one trigger of the given class type.
     * Uses the custom JQ_STRING function to extract the first matching trigger type from the JSON array.
     *
     * @param triggerClass the trigger class to filter by, or {@code null} to match all flows
     * @return a jOOQ {@link Condition}
     */
    public static Condition findTriggerClassCondition(Class<? extends io.kestra.core.models.triggers.AbstractTrigger> triggerClass) {
        if (triggerClass == null) {
            return DSL.trueCondition();
        }
        Field<String> matchedType = DSL.field(
            "JQ_STRING(\"value\", CONCAT('.triggers[]? | select(.type == \"', {0}, '\") | .type'))",
            String.class,
            DSL.val(triggerClass.getName(), String.class)
        );
        return matchedType.isNotNull();
    }

    public static Condition findCondition(Object labels, QueryFilter.Op operation) {
        List<Condition> conditions = new ArrayList<>();

        if (labels instanceof Map<?, ?> labelValues) {
            labelValues.forEach((key, value) ->
            {
                Field<String> valueField = DSL.field("JQ_STRING(\"value\", CONCAT('.labels[]? | select(.key == \"', {0}, '\") | .value'))", String.class, DSL.val(key, String.class));
                Condition condition = switch (operation) {
                    case EQUALS -> value == null ? valueField.isNull() : valueField.eq((String) value);
                    case NOT_EQUALS -> value == null ? valueField.isNotNull() : valueField.isNull().or(valueField.ne((String) value));
                    default -> throw new UnsupportedOperationException("Unsupported operation: " + operation);
                };

                conditions.add(condition);
            });

        }
        return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
    }
}
