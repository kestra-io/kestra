package io.kestra.repository.mysql;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.*;

import static io.kestra.core.models.QueryFilter.Op.EQUALS;

public abstract class MysqlFlowRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(Arrays.asList("namespace", "id"), query));
        }

        if (labels != null) {
            labels.forEach((key, value) -> {
                Field<Boolean> valueField = DSL.field("JSON_CONTAINS(value, JSON_ARRAY(JSON_OBJECT('key', '" + key + "', 'value', '" + value + "')), '$.labels')", Boolean.class);
                conditions.add(valueField.eq(value != null));
            });
        }

        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }

    public static Condition findSourceCodeCondition(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, String query) {
        return jdbcRepository.fullTextCondition(Collections.singletonList("source_code"), query);
    }

    public static Condition findCondition(Object labels, QueryFilter.Op operation) {
        List<Condition> conditions = new ArrayList<>();

        if (labels instanceof Map<?, ?> labelValues) {
            labelValues.forEach((key, value) -> {
                Field<Boolean> valueField = DSL.field("JSON_CONTAINS(value, JSON_ARRAY(JSON_OBJECT('key', '" + key + "', 'value', '" + value + "')), '$.labels')", Boolean.class);
               if(operation.equals(EQUALS))
                conditions.add(valueField.eq(value != null));

            });
        }
        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }
}
