package io.kestra.repository.mysql;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.*;

import static io.kestra.core.models.QueryFilter.Op.EQUALS;

public abstract class MysqlExecutionRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<Execution> jdbcRepository, String query, Map<String, String> labels) {
            List<Condition> conditions = new ArrayList<>();

            if (query != null) {
                conditions.add(jdbcRepository.fullTextCondition(Arrays.asList("namespace", "flow_id", "id"), query));
            }

            if (labels != null) {
                labels.forEach((key, value) -> {
                    Field<Boolean> valueField = DSL.field("JSON_CONTAINS(value, JSON_ARRAY(JSON_OBJECT('key', '" + key + "', 'value', '" + value + "')), '$.labels')", Boolean.class);
                    conditions.add(valueField.eq(value != null));
                });
            }

            return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }

    public static Condition findLabelCondition(Either<Map<?, ?>, String> input, QueryFilter.Op operation) {
        List<Condition> conditions = new ArrayList<>();
        List<Condition> inConditions = new ArrayList<>();
        if (input.isRight()) {
            var query = input.getRight();
            if (Objects.requireNonNull(operation) == QueryFilter.Op.CONTAINS) {
                conditions.add
                    (DSL.condition(
                    "JSON_SEARCH(value, 'one', CONCAT('%', ?, '%'), NULL, '$.labels[*].key') IS NOT NULL", query)
                    .or(DSL.condition(
                        "JSON_SEARCH(value, 'one', CONCAT('%', ?, '%'), NULL, '$.labels[*].value') IS NOT NULL", query)
                    ));
            } else {
                throw new UnsupportedOperationException("Unsupported operation for query: " + operation);
            }
        } else {
            var labels = input.getLeft();
            labels.forEach((key, value) -> {
                String sql = "JSON_CONTAINS(value, JSON_ARRAY(JSON_OBJECT('key', '" + key + "', 'value', '" + value + "')), '$.labels')";
                switch(operation){
                    case EQUALS ->
                        conditions.add(DSL.condition(sql));
                    case NOT_EQUALS, NOT_IN ->
                        conditions.add(DSL.not(DSL.condition(sql)));
                    case IN ->
                        inConditions.add(DSL.condition(sql));
                }
            });
        }

        if(!inConditions.isEmpty()){
            conditions.add(DSL.or(inConditions));
        }
        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }

}
