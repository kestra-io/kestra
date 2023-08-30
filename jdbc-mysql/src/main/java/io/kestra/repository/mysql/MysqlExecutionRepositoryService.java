package io.kestra.repository.mysql;

import io.kestra.core.models.executions.Execution;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class MysqlExecutionRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<Execution> jdbcRepository, String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(Arrays.asList("namespace", "flow_id", "id"), query));
        }

        if (labels != null) {
            labels.forEach((key, value) -> {
                Field<String> keyField = DSL.field("JSON_SEARCH(value, 'one', '" + key + "', NULL, '$.labels[*].key')", String.class);
                conditions.add(keyField.isNotNull());

                Field<String> valueField = DSL.field("JSON_SEARCH(value, 'one', '" + value + "', NULL, '$.labels[*].value')", String.class);
                conditions.add(valueField.isNotNull());
            });
        }

        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }
}
