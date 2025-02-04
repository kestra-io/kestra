package io.kestra.repository.mysql;

import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.kestra.jdbc.JdbcTableConfig;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.Sort.Order;
import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("this-escape")
@MysqlRepositoryEnabled
@EachBean(JdbcTableConfig.class)
public class MysqlRepository<T> extends AbstractJdbcRepository<T> {

    @Inject
    public MysqlRepository(@Parameter JdbcTableConfig jdbcTableConfig,
                           QueueService queueService,
                           JooqDSLContextWrapper dslContextWrapper) {
        super(jdbcTableConfig, queueService, dslContextWrapper);
        this.table = DSL.table(DSL.quotedName(this.getTable().getName()));
    }

    /** {@inheritDoc} **/
    @Override
    public Condition fullTextCondition(List<String> fields, String query) {
        if (query == null || query.equals("*")) {
            return DSL.trueCondition();
        }

        String match = Arrays
            .stream(query.split("\\p{IsPunct}"))
            .filter(s -> s.length() >= 3)
            .map(s -> "+" + s + "*")
            .collect(Collectors.joining(" "));

        if (match.isEmpty()) {
            return DSL.falseCondition();
        }

        return DSL.condition("MATCH (" + String.join(", ", fields) + ") AGAINST (? IN BOOLEAN MODE)", match);
    }

    public <R extends Record, E> ArrayListTotal<E> fetchPage(DSLContext context, SelectConditionStep<R> select, Pageable pageable, RecordMapper<R, E> mapper) {
        List<E> map = this.pageable(select, pageable)
            .fetch()
            .map(mapper);

        return dslContextWrapper.transactionResult(configuration -> new ArrayListTotal<>(
            map,
            DSL.using(configuration).fetchOne("SELECT FOUND_ROWS()").into(Integer.class)
        ));
    }

    @Override
    public <R extends Record> Select<R> buildPageQuery(DSLContext context, SelectConditionStep<R> select){
        return this.sort(select, Pageable.from(Sort.of(Order.asc("timestamp"))));
    }

    public Field<Integer> weekFromTimestamp(Field<Timestamp> timestampField) {
        // DAYOFWEEK > 5 means you have less than 3 days in the first week of the year so we choose mode 2 (see https://www.w3resource.com/mysql/date-and-time-functions/mysql-week-function.php)
        return DSL.when(
            DSL.field("DAYOFWEEK(CONCAT(YEAR({0}), '-01-01')) > 5", Boolean.class, timestampField),
            DSL.field("WEEK({0}, 2)", Integer.class, timestampField)
        ).otherwise(DSL.field("WEEK({0}, 3)", Integer.class, timestampField));
    }
}
