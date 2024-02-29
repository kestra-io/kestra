package io.kestra.repository.postgres;

import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import lombok.SneakyThrows;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import jakarta.annotation.Nullable;

public class PostgresRepository<T> extends io.kestra.jdbc.AbstractJdbcRepository<T> {
    public PostgresRepository(Class<T> cls, ApplicationContext applicationContext) {
        super(cls, applicationContext);
    }

    @Override
    public Condition fullTextCondition(List<String> fields, String query) {
        if (query == null || query.equals("*")) {
            return DSL.trueCondition();
        }

        if (fields.size() > 1) {
            throw new IllegalArgumentException("Invalid fullTextCondition" + fields);
        }

        return DSL.condition(fields.get(0) + " @@ FULLTEXT_SEARCH(?)", query);
    }

    @SneakyThrows
    @Override
    public Map<Field<Object>, Object> persistFields(T entity) {
        Map<Field<Object>, Object> fields = super.persistFields(entity);

        String json = JdbcMapper.of().writeValueAsString(entity);
        fields.replace(AbstractJdbcRepository.field("value"), DSL.val(JSONB.valueOf(json)));

        return fields;
    }

    @SneakyThrows
    public void persist(T entity, DSLContext context, @Nullable  Map<Field<Object>, Object> fields) {
        Map<Field<Object>, Object> finalFields = fields == null ? this.persistFields(entity) : fields;

        context
            .insertInto(table)
            .set(AbstractJdbcRepository.field("key"), key(entity))
            .set(finalFields)
            .onConflict(AbstractJdbcRepository.field("key"))
            .doUpdate()
            .set(finalFields)
            .execute();
    }

    @SuppressWarnings("unchecked")
    public <R extends Record, E> ArrayListTotal<E> fetchPage(DSLContext context, SelectConditionStep<R> select, Pageable pageable, RecordMapper<R, E> mapper) {
        Result<Record> results = this.limit(
            context.select(DSL.asterisk(), DSL.count().over().as("total_count"))
                .from(this
                    .sort(select, pageable)
                    .asTable("page")
                )
                .where(DSL.trueCondition()),
            pageable
        )
            .fetch();

        Integer totalCount = results.size() > 0 ? results.get(0).get("total_count", Integer.class) : 0;

        List<E> map = results
            .map((Record record) -> mapper.map((R) record));

        return new ArrayListTotal<>(map, totalCount);
    }
}
