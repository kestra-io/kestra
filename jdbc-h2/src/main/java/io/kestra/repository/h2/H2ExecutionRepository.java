package io.kestra.repository.h2;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.utils.DateUtils;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.runner.AbstractJdbcExecutorStateStorage;
import io.kestra.jdbc.services.JdbcFilterService;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.*;

@Singleton
@H2RepositoryEnabled
public class H2ExecutionRepository extends AbstractJdbcExecutionRepository {
    @Inject
    public H2ExecutionRepository(@Named("executions") H2Repository<Execution> repository,
                                 ApplicationContext applicationContext,
                                 AbstractJdbcExecutorStateStorage executorStateStorage,
                                 JdbcFilterService filterService) {
        super(repository, applicationContext, executorStateStorage, filterService);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(List.of("fulltext"), query));
        }

        if (labels != null) {
            labels.forEach((key, value) -> {
                Field<String> valueField = DSL.field("JQ_STRING(\"value\", '.labels[]? | select(.key == \"" + key + "\") | .value')", String.class);
                if (value == null) {
                    conditions.add(valueField.isNull());
                } else {
                    conditions.add(valueField.eq(value));
                }
            });
        }

        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }

    @Override
    protected Condition findLabelCondition(Either<Map<?, ?>, String> input, QueryFilter.Op operation) {
        List<Condition> conditions = new ArrayList<>();
        List<Condition> inConditions = new ArrayList<>();
        if (input.isRight()) {
            var query = input.right().get();
            Field<String> valueField = DSL.field("JQ_STRING(\"value\", '.labels[]? | .value')", String.class);
            if (Objects.requireNonNull(operation) == QueryFilter.Op.CONTAINS) {
                conditions.add(valueField.contains(query));
            } else {
                throw new UnsupportedOperationException("Unsupported operation for query: " + operation);
            }
        } else {
            var labels = input.left().get();
            labels.forEach((key, value) -> {
                Field<String> valueField = DSL.field("JQ_STRING(\"value\", '.labels[]? | select(.key == \"" + key + "\") | .value')", String.class);
                switch (operation) {
                    case EQUALS -> conditions.add(value == null ? valueField.isNull() : valueField.eq((String) value));
                    case NOT_EQUALS, NOT_IN ->
                        conditions.add(value == null ? valueField.isNotNull() : valueField.isNull().or(valueField.ne((String) value)));
                    case IN -> inConditions.add(value == null ? valueField.isNull() : valueField.eq((String) value));
                    default -> throw new UnsupportedOperationException("Unsupported operation: " + operation);
                }
            });
        }

        if (!inConditions.isEmpty()) {
            conditions.add(DSL.or(inConditions));
        }
        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        switch (groupType) {
            case MONTH:
                return DSL.field("FORMATDATETIME(\"" + dateField + "\", 'yyyy-MM')", Date.class);
            case WEEK:
                return DSL.field("FORMATDATETIME(\"" + dateField + "\", 'YYYY-ww')", Date.class);
            case DAY:
                return DSL.field("FORMATDATETIME(\"" + dateField + "\", 'yyyy-MM-dd')", Date.class);
            case HOUR:
                return DSL.field("FORMATDATETIME(\"" + dateField + "\", 'yyyy-MM-dd HH:00:00')", Date.class);
            case MINUTE:
                return DSL.field("FORMATDATETIME(\"" + dateField + "\", 'yyyy-MM-dd HH:mm:00')", Date.class);
            default:
                throw new IllegalArgumentException("Unsupported GroupType: " + groupType);
        }
    }
}