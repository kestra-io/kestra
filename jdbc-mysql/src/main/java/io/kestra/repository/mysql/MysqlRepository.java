package io.kestra.repository.mysql;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.Result;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.kestra.jdbc.JdbcTableConfig;
import io.kestra.jdbc.JooqDSLContextWrapper;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.Sort.Order;
import jakarta.inject.Inject;

@SuppressWarnings("this-escape")
@Requires(condition = MysqlRepository.MysqlCondition.class)
@EachBean(JdbcTableConfig.class)
public class MysqlRepository<T> extends AbstractJdbcRepository<T> {

    @Inject
    public MysqlRepository(@Parameter JdbcTableConfig jdbcTableConfig,
        JooqDSLContextWrapper dslContextWrapper) {
        super(jdbcTableConfig, dslContextWrapper);
        this.table = DSL.table(DSL.quotedName(this.getTable().getName()));
    }

    /** {@inheritDoc} **/
    @Override
    public Condition fullTextCondition(List<String> fields, String query) {
        if (query == null || query.equals("*")) {
            return DSL.noCondition();
        }

        String escaped = escapeForLike(query);
        String pattern = "%" + escaped + "%";

        Condition likeCondition = DSL.falseCondition();
        for (String fieldName : fields) {
            Field<String> f = DSL.field(fieldName, String.class);
            likeCondition = likeCondition.or(f.like(pattern, '\\'));
        }

        String booleanQuery = Arrays.stream(query.split("\\p{IsPunct}|\\s+"))
            .filter(s -> s.length() >= 3)
            .map(s -> "+" + s + "*")
            .collect(Collectors.joining(" "));

        Condition fulltextCondition;
        if (booleanQuery.isEmpty()) {
            fulltextCondition = DSL.falseCondition();
        } else {
            fulltextCondition = DSL.condition(
                "MATCH (" + String.join(", ", fields) + ") AGAINST (? IN BOOLEAN MODE)",
                booleanQuery
            );
        }

        return fulltextCondition.or(likeCondition);
    }

    private static String escapeForLike(String s) {
        return s
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }

    @Override
    public <R extends Record, E> ArrayListTotal<E> fetchPage(DSLContext context, SelectConditionStep<R> select, Pageable pageable, RecordMapper<R, E> mapper) {
        int rows = context.fetchCount(select);
        Result<R> records = this.pageable(select, pageable).fetch();
        return new ArrayListTotal<>(records.map(mapper), rows);
    }

    @Override
    public <R extends Record> Select<R> buildQuery(DSLContext context, SelectConditionStep<R> select, String orderField) {
        return this.sort(select, Pageable.from(Sort.of(Order.asc(orderField))));
    }

    public Field<Integer> weekFromTimestamp(Field<Timestamp> timestampField) {
        // DAYOFWEEK > 5 means you have less than 3 days in the first week of the year so we choose mode 2 (see https://www.w3resource.com/mysql/date-and-time-functions/mysql-week-function.php)
        return DSL.when(
            DSL.field("DAYOFWEEK(CONCAT(YEAR({0}), '-01-01')) > 5", Boolean.class, timestampField),
            DSL.field("WEEK({0}, 2)", Integer.class, timestampField)
        ).otherwise(DSL.field("WEEK({0}, 3)", Integer.class, timestampField));
    }

    // We need to create H2 repositories for the queue as it uses an H2Repository named 'queue',
    // we may find a way to only create this one at some point as here we create unnecessary beans.
    static class MysqlCondition implements io.micronaut.context.condition.Condition {
        @Override
        public boolean matches(ConditionContext context) {
            boolean isRepository = ((Optional<String>) context.get("kestra.repository.type", String.class)).map(it -> "mysql".equals(it)).orElse(false);
            boolean isQueue = ((Optional<String>) context.get("kestra.queue.type", String.class)).map(it -> "mysql".equals(it)).orElse(false);
            return isRepository || isQueue;
        }
    }
}
