package io.kestra.repository.mysql;

import io.kestra.core.models.DeletedInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MysqlRepository<T>  extends AbstractJdbcRepository<T> {
    public MysqlRepository(Class<T> cls, ApplicationContext applicationContext) {
        super(cls, applicationContext);
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

        return dslContext.transactionResult(configuration -> new ArrayListTotal<>(
            map,
            DSL.using(configuration).fetchOne("SELECT FOUND_ROWS()").into(Integer.class)
        ));
    }
}
