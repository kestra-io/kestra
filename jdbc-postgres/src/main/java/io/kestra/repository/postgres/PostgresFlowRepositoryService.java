package io.kestra.repository.postgres;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowScope;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.*;

import static io.kestra.core.models.QueryFilter.Op.EQUALS;
import static io.kestra.jdbc.repository.AbstractJdbcRepository.field;
import static io.kestra.jdbc.repository.AbstractJdbcTriggerRepository.NAMESPACE_FIELD;

public abstract class PostgresFlowRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<Flow> jdbcRepository, String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query));
        }

        if (labels != null) {
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


    public static Condition findCondition(Object labels, QueryFilter.Op operation) {
        List<Condition> conditions = new ArrayList<>();

        if (labels instanceof Map<?, ?> labelValues) {
            labelValues.forEach((key, value) -> {
                String sql = "value -> 'labels' @> '[{\"key\":\"" + key + "\", \"value\":\"" + value + "\"}]'";
                if (operation.equals(EQUALS)) {
                    conditions.add(DSL.condition(sql));
                } else {
                    conditions.add(DSL.not(DSL.condition(sql)));
                }
            });
        }
        return conditions.isEmpty() ? DSL.trueCondition() : DSL.or(conditions);
    }


}
