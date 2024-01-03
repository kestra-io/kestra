package io.kestra.repository.mysql;

import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MysqlRepository<T>  extends AbstractJdbcRepository<T> {
    public MysqlRepository(Class<T> cls, ApplicationContext applicationContext) {
        super(cls, applicationContext);

        this.table = DSL.table(DSL.quotedName(this.getTable().getName()));
    }

    public Condition fullTextCondition(List<String> fields, String query) {
        if (query == null || query.equals("*")) {
            return DSL.trueCondition();
        }

        String match = Arrays
            .stream(query.split("\\p{IsPunct}"))
            .filter(s -> s.length() >= 3)
            .map(s -> "+" + s + "*")
            .collect(Collectors.joining(" "));

        if (match.length() == 0) {
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

    public Field<Integer> weekFromTimestamp(Field<Timestamp> timestampField) {
        // DAYOFWEEK > 5 means you have less than 3 days in the first week of the year so we choose mode 2 (see https://www.w3resource.com/mysql/date-and-time-functions/mysql-week-function.php)
        return DSL.when(
            DSL.field("DAYOFWEEK(CONCAT(YEAR({0}), '-01-01')) > 5", Boolean.class, timestampField),
            DSL.field("WEEK({0}, 2)", Integer.class, timestampField)
        ).otherwise(DSL.field("WEEK({0}, 3)", Integer.class, timestampField));
    }
}
