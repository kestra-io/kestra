package io.kestra.repository.postgres;

import java.util.*;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.AbstractJdbcRepository;

public abstract class PostgresExecutionRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<Execution> jdbcRepository, String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query));
        }

        if (labels != null) {
            labels.forEach((key, value) ->
            {
                conditions.add(
                    DSL.condition("value -> 'labels' @> jsonb_build_array(jsonb_build_object('key', {0}::text, 'value', {1}::text))", DSL.val(key, String.class), DSL.val(value, String.class))
                );
            });
        }

        return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
    }

    public static Condition findLabelCondition(Either<Map<?, ?>, String> input, QueryFilter.Op operation) {
        List<Condition> conditions = new ArrayList<>();
        List<Condition> inConditions = new ArrayList<>();
        if (input.isRight()) {
            var query = input.right().get();
            switch (Objects.requireNonNull(operation)) {
                case CONTAINS -> conditions.add(labelContainsCondition(query));
                case NOT_CONTAINS -> conditions.add(labelContainsCondition(query).not());
                case IS_NULL -> conditions.add(labelKeyCondition(query).not());
                case IS_NOT_NULL -> conditions.add(labelKeyCondition(query));
                default -> throw new UnsupportedOperationException("Unsupported operation for query: " + operation);
            }
        } else {
            var labels = input.getLeft();
            labels.forEach((key, value) ->
            {
                Field<Boolean> labelMatches = DSL.field(
                    "COALESCE(value -> 'labels' @> jsonb_build_array(jsonb_build_object('key', {0}::text, 'value', {1}::text)), false)",
                    Boolean.class,
                    DSL.val((String) key, String.class),
                    DSL.val((String) value, String.class)
                );
                switch (operation) {
                    case EQUALS -> conditions.add(labelMatches.isTrue());
                    case NOT_EQUALS, NOT_IN -> conditions.add(labelMatches.isFalse());
                    case IN -> inConditions.add(labelMatches.isTrue());
                    case IS_NULL -> conditions.add(labelKeyCondition((String) key).not());
                    case IS_NOT_NULL -> conditions.add(labelKeyCondition((String) key));
                    default -> throw new UnsupportedOperationException("Unsupported operation: " + operation);
                }
            });
        }

        if (!inConditions.isEmpty()) {
            conditions.add(DSL.or(inConditions));
        }
        return conditions.isEmpty() ? DSL.noCondition() : DSL.and(conditions);
    }

    private static Condition labelContainsCondition(String query) {
        String sql = "EXISTS (" +
            " SELECT 1 FROM jsonb_array_elements(COALESCE(value -> 'labels', '[]'::jsonb)) AS lbl" +
            " WHERE lower(lbl ->> 'value') LIKE lower('%' || ? || '%')" +
            "    OR lower(lbl ->> 'key') LIKE lower('%' || ? || '%')" +
            ")";
        return DSL.condition(sql, query, query);
    }

    private static Condition labelKeyCondition(String key) {
        return DSL.condition(
            "EXISTS (" +
                " SELECT 1 FROM jsonb_array_elements(COALESCE(value -> 'labels', '[]'::jsonb)) AS lbl" +
                " WHERE lbl ->> 'key' = ?" +
                ")",
            key
        );
    }

    public static Condition statesFilter(List<State.Type> state) {
        return DSL.or(
            state
                .stream()
                .map(Enum::name)
                .map(
                    s -> DSL.field("state_current")
                        .eq(
                            DSL.field("CAST(? AS state_type)", SQLDataType.VARCHAR(50).getArrayType(), s)
                        )
                )
                .toList()
        );
    }

}
