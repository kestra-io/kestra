package io.kestra.repository.postgres;

import io.kestra.core.models.flows.Flow;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class PostgresFlowRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<Flow> jdbcRepository, String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query));
        }

        if (labels != null)  {
            labels.forEach((key, value) -> {
                String sql = "value -> 'labels' @> '[{\"key\":\"" + key + "\", \"value\":\"" + value + "\"}]'";
                conditions.add(DSL.condition(sql));
            });
        }

        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }

    public static Condition findSourceCodeCondition(AbstractJdbcRepository<Flow> jdbcRepository, String query) {
        return jdbcRepository.fullTextCondition(Collections.singletonList("FULLTEXT_INDEX(source_code)"), query);
    }
}
