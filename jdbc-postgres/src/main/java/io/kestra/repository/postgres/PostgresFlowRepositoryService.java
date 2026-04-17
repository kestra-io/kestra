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
                conditions.add(DSL.condition("value -> 'labels' @> jsonb_build_array(jsonb_build_object('key', {0}::text, 'value', {1}::text))", DSL.val(key, String.class), DSL.val(value, String.class)));
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

        if (labels instanceof Map<?, ?> labelValues) {
            labelValues.forEach((key, value) ->
            {
                if (operation.equals(EQUALS)) {
                    conditions.add(DSL.condition("value -> 'labels' @> jsonb_build_array(jsonb_build_object('key', {0}::text, 'value', {1}::text))", DSL.val(key, String.class), DSL.val(value, String.class)));
                } else if (operation.equals(QueryFilter.Op.NOT_EQUALS)) {
                    // For NOT_EQUALS: match flows where the label key doesn't exist OR the label value is different
                    String extractValueSql = "(SELECT jsonb_path_query_first(value, '$.labels[*] ? (@.key == $labelKey).value', jsonb_build_object('labelKey', {0}::text))#>>'{}')";
                    Field<String> extractedValue = DSL.field(extractValueSql, String.class, DSL.val(key, String.class));
                    conditions.add(extractedValue.isNull().or(extractedValue.ne((String) value)));
                }
            });
        }
        return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
    }

}
