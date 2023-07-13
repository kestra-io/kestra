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
                Field<String> keyField = DSL.field("JSON_VALUE(value, '$.labels[*].key' NULL ON EMPTY)", String.class);
                conditions.add(keyField.eq(key));

                Field<String> valueField = DSL.field("JSON_VALUE(value, '$.labels[*].value' NULL ON EMPTY)", String.class);
                if (value == null) {
                    conditions.add(valueField.isNotNull());
                } else {
                    conditions.add(valueField.eq(value));
                }
            });
        }

        return conditions.size() == 0 ? DSL.trueCondition() : DSL.and(conditions);
    }
}
