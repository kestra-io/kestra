package io.kestra.repository.mysql;

import io.kestra.core.models.flows.Flow;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.*;

public abstract class MysqlFlowRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<Flow> jdbcRepository, String query, Map<String, String> labels) {
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

    public static Condition findSourceCodeCondition(AbstractJdbcRepository<Flow> jdbcRepository, String query) {
        return jdbcRepository.fullTextCondition(Collections.singletonList("source_code"), query);
    }
}
