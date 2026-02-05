package io.kestra.repository.postgres;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.kestra.core.models.QueryFilter.Op.EQUALS;

public abstract class PostgresFlowRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, String query, Map<String, String> labels) {
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

    public static Condition findSourceCodeCondition(AbstractJdbcRepository<? extends FlowInterface> jdbcRepository, String query) {
        return jdbcRepository.fullTextCondition(Collections.singletonList("FULLTEXT_INDEX(source_code)"), query);
    }


    public static Condition findCondition(Object labels, QueryFilter.Op operation) {
        List<Condition> conditions = new ArrayList<>();

        if (labels instanceof Map<?, ?> labelValues) {
            labelValues.forEach((key, value) -> {
                String sql = "value -> 'labels' @> '[{\"key\":\"" + key + "\", \"value\":\"" + value + "\"}]'";
                if (operation.equals(EQUALS)) {
                    conditions.add(DSL.condition(sql));
                } else if (operation.equals(QueryFilter.Op.NOT_EQUALS)) {
                    // For NOT_EQUALS: match flows where the label key doesn't exist OR the label value is different
                    String extractValueSql = "(SELECT jsonb_path_query_first(value, '$.labels[*] ? (@.key == \"" + key + "\").value')#>>'{}')";
                    Field<String> extractedValue = DSL.field(extractValueSql, String.class);
                    conditions.add(extractedValue.isNull().or(extractedValue.ne((String) value)));
                }
            });
        }
        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }


}
