package io.kestra.repository.mysql;

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

import java.sql.Timestamp;
import java.util.*;

import static io.kestra.core.models.QueryFilter.Op.EQUALS;

@Singleton
@MysqlRepositoryEnabled
public class MysqlExecutionRepository extends AbstractJdbcExecutionRepository {
    @Inject
    public MysqlExecutionRepository(@Named("executions") MysqlRepository<Execution> repository,
                                    ApplicationContext applicationContext,
                                    AbstractJdbcExecutorStateStorage executorStateStorage,
                                    JdbcFilterService filterService) {
        super(repository, applicationContext, executorStateStorage, filterService);
    }

    @Override
    protected Condition findCondition(String query, Map<String, String> labels) {
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

    @Override
    protected Condition findLabelCondition(Either<Map<?, ?>, String> input, QueryFilter.Op operation) {
        List<Condition> conditions = new ArrayList<>();
        List<Condition> inConditions = new ArrayList<>();
        if (input.isRight()) {
            var query = input.getRight();
            // This will check if any label value contains the query substring
            if (Objects.requireNonNull(operation) == QueryFilter.Op.CONTAINS) {
                String sql = "EXISTS (" +
                    "SELECT 1 FROM JSON_TABLE(value, '$.labels[*]' COLUMNS(label_value VARCHAR(255) PATH '$.value')) AS lbl " +
                    "WHERE lbl.label_value LIKE CONCAT('%', ?, '%')" +
                    ")";
                conditions.add(DSL.condition(sql, query));
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

    @Override
    protected Field<Integer> weekFromTimestamp(Field<Timestamp> timestampField) {
        return this.jdbcRepository.weekFromTimestamp(timestampField);
    }

    @Override
    protected Field<Date> formatDateField(String dateField, DateUtils.GroupType groupType) {
        switch (groupType) {
            case MONTH:
                return DSL.field("DATE_FORMAT({0}, '%Y-%m')", Date.class, DSL.field(dateField));
            case WEEK:
                return DSL.field("DATE_FORMAT({0}, '%x-%v')", Date.class, DSL.field(dateField));
            case DAY:
                return DSL.field("DATE({0})", Date.class, DSL.field(dateField));
            case HOUR:
                return DSL.field("DATE_FORMAT({0}, '%Y-%m-%d %H:00:00')", Date.class, DSL.field(dateField));
            case MINUTE:
                return DSL.field("DATE_FORMAT({0}, '%Y-%m-%d %H:%i:00')", Date.class, DSL.field(dateField));
            default:
                throw new IllegalArgumentException("Unsupported GroupType: " + groupType);
        }
    }
}
