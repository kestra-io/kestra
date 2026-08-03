package io.kestra.repository.mysql;

import java.util.*;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.jdbc.AbstractJdbcRepository;

import static io.kestra.core.models.QueryFilter.Op.EQUALS;
import static io.kestra.core.models.QueryFilter.Op.NOT_EQUALS;

public abstract class MysqlFlowRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(Arrays.asList("namespace", "id"), query));
        }

        if (labels != null) {
            labels.forEach((key, value) ->
            {
                Field<Boolean> valueField = DSL
                    .field("JSON_CONTAINS(value, JSON_ARRAY(JSON_OBJECT('key', {0}, 'value', {1})), '$.labels')", Boolean.class, DSL.val(key, String.class), DSL.val(value, String.class));
                conditions.add(valueField.eq(value != null));
            });
        }

        return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
    }

    public static Condition findSourceCodeCondition(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, String query) {
        return jdbcRepository.fullTextCondition(Collections.singletonList("source_code"), query);
    }

    /**
     * Builds a condition that matches flows containing at least one trigger of the given class type.
     * Uses JSON_SEARCH to check if the type value exists anywhere in the triggers array.
     *
     * @param triggerClass the trigger class to filter by, or {@code null} to match all flows
     * @return a jOOQ {@link Condition}
     */
    public static Condition findTriggerClassCondition(Class<? extends io.kestra.core.models.triggers.AbstractTrigger> triggerClass) {
        if (triggerClass == null) {
            return DSL.trueCondition();
        }
        return DSL.condition(
            "JSON_SEARCH(`value`, 'one', {0}, NULL, '$.triggers[*].type') IS NOT NULL",
            DSL.val(triggerClass.getName(), String.class)
        );
    }

    public static Condition findCondition(Object labels, QueryFilter.Op operation) {
        List<Condition> conditions = new ArrayList<>();
        List<Condition> inConditions = new ArrayList<>();

        if (labels instanceof String label) {
            switch (operation) {
                case CONTAINS -> conditions.add(labelContainsCondition(label));
                case NOT_CONTAINS -> conditions.add(labelContainsCondition(label).not());
                case IS_NULL -> conditions.add(labelKeyCondition(label).not());
                case IS_NOT_NULL -> conditions.add(labelKeyCondition(label));
                default -> throw new UnsupportedOperationException("Unsupported operation: " + operation);
            }
        } else if (labels instanceof Map<?, ?> labelValues) {
            labelValues.forEach((key, value) ->
            {
                Field<Boolean> valueField = DSL.field(
                    "JSON_CONTAINS(value, JSON_ARRAY(JSON_OBJECT('key', {0}, 'value', {1})), '$.labels')", Boolean.class, DSL.val(key, String.class), DSL.val((String) value, String.class)
                );
                Field<Boolean> labelMatches = DSL.coalesce(valueField, false);
                if (operation.equals(EQUALS))
                    conditions.add(labelMatches.eq(value != null));
                else if (operation.equals(QueryFilter.Op.IN))
                    inConditions.add(labelMatches.eq(value != null));
                else if (operation.equals(QueryFilter.Op.NOT_IN))
                    conditions.add(labelMatches.ne(value != null));
                else if (operation.equals(NOT_EQUALS)) {
                    conditions.add(labelMatches.ne(value != null));
                } else if (operation.equals(QueryFilter.Op.IS_NULL)) {
                    conditions.add(labelKeyCondition((String) key).not());
                } else if (operation.equals(QueryFilter.Op.IS_NOT_NULL)) {
                    conditions.add(labelKeyCondition((String) key));
                } else {
                    throw new UnsupportedOperationException("Unsupported operation: " + operation);
                }
            });
        }
        if (!inConditions.isEmpty()) {
            conditions.add(DSL.or(inConditions));
        }
        return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
    }

    private static Condition labelContainsCondition(String query) {
        return DSL.condition(
            "JSON_SEARCH(value, 'one', CONCAT('%', ?, '%'), NULL, '$.labels[*].key') IS NOT NULL", query
        )
            .or(
                DSL.condition(
                    "JSON_SEARCH(value, 'one', CONCAT('%', ?, '%'), NULL, '$.labels[*].value') IS NOT NULL", query
                )
            );
    }

    private static Condition labelKeyCondition(String key) {
        return DSL.condition(
            "JSON_SEARCH(value, 'one', ?, NULL, '$.labels[*].key') IS NOT NULL", key
        );
    }
}
