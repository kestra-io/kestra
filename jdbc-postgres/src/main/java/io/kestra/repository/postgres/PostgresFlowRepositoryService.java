package io.kestra.repository.postgres;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.jdbc.AbstractJdbcRepository;

import static io.kestra.core.models.QueryFilter.Op.EQUALS;

public abstract class PostgresFlowRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query));
        }

        if (labels != null) {
            labels.forEach((key, value) ->
            {
                conditions.add(
                    DSL.condition("value -> 'labels' @> jsonb_build_array(jsonb_build_object('key', {0}::text, 'value', {1}::text))", DSL.val(key, String.class), DSL.val(value, String.class))
                );
            });
        }

        return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
    }

    public static Condition findSourceCodeCondition(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, String query) {
        return jdbcRepository.fullTextCondition(Collections.singletonList("FULLTEXT_INDEX(source_code)"), query);
    }

    /**
     * Builds a condition that matches flows containing at least one trigger of the given class type.
     * Uses jsonb_path_exists to check if any element in the triggers array has a matching type field.
     *
     * @param triggerClass the trigger class to filter by, or {@code null} to match all flows
     * @return a jOOQ {@link Condition}
     */
    public static Condition findTriggerClassCondition(Class<? extends io.kestra.core.models.triggers.AbstractTrigger> triggerClass) {
        if (triggerClass == null) {
            return DSL.trueCondition();
        }
        return DSL.condition(
            "jsonb_path_exists(value, '$.triggers[*] ? (@.type == $triggerType)', jsonb_build_object('triggerType', {0}::text))",
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
                Field<Boolean> labelMatches = DSL.field(
                    "COALESCE(value -> 'labels' @> jsonb_build_array(jsonb_build_object('key', {0}::text, 'value', {1}::text)), false)",
                    Boolean.class,
                    DSL.val(key, String.class),
                    DSL.val(value, String.class)
                );
                if (operation.equals(EQUALS)) {
                    conditions.add(labelMatches.isTrue());
                } else if (operation.equals(QueryFilter.Op.IN)) {
                    inConditions.add(labelMatches.isTrue());
                } else if (operation.equals(QueryFilter.Op.NOT_IN)) {
                    conditions.add(labelMatches.isFalse());
                } else if (operation.equals(QueryFilter.Op.NOT_EQUALS)) {
                    conditions.add(labelMatches.isFalse());
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
        String sql = "EXISTS (" +
            " SELECT 1 FROM jsonb_array_elements(COALESCE(value -> 'labels', '[]'::jsonb)) AS lbl" +
            " WHERE lower(lbl ->> 'value') LIKE lower('%' || ? || '%')" +
            "    OR lower(lbl ->> 'key') LIKE lower('%' || ? || '%')" +
            ")";
        return DSL.condition(sql, query, query);
    }

    private static Condition labelKeyCondition(String key) {
        return DSL.condition(
            "EXISTS (" +
                " SELECT 1 FROM jsonb_array_elements(COALESCE(value -> 'labels', '[]'::jsonb)) AS lbl" +
                " WHERE lbl ->> 'key' = ?" +
                ")",
            key
        );
    }

}
