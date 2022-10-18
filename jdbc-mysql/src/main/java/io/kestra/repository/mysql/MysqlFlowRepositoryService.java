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

        if (labels != null)  {
            labels.forEach((key, value) -> {
                Field<String> field = DSL.field("JSON_VALUE(value, '$.labels." + key + "' NULL ON EMPTY)", String.class);

                if (value == null) {
                    conditions.add(field.isNotNull());
                } else {
                    conditions.add(field.eq(value));
                }
            });
        }

        return conditions.size() == 0 ? DSL.trueCondition() : DSL.and(conditions);
    }

    public static Condition findSourceCodeCondition(AbstractJdbcRepository<Flow> jdbcRepository, String query) {
        return jdbcRepository.fullTextCondition(Collections.singletonList("source_code"), query);
    }
}
