package io.kestra.repository.postgres;

import io.kestra.core.models.DeletedInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.jdbc.AbstractJdbcRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import lombok.SneakyThrows;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;

public class PostgresRepository<T extends DeletedInterface> extends AbstractJdbcRepository<T> {
    public PostgresRepository(Class<T> cls, ApplicationContext applicationContext) {
        super(cls, applicationContext);
    }

    @Override
    public Condition fullTextCondition(List<String> fields, String query) {
        if (fields.size() > 1) {
            throw new IllegalArgumentException("Invalid fullTextCondition" + fields);
        }

        return DSL.condition(fields.get(0) + " @@ FULLTEXT_SEARCH(?)", query);
    }

    @SneakyThrows
    public void persist(T entity, Map<Field<Object>, Object> fields) {
        if (fields == null) {
            fields = this.persistFields(entity);
        }

        String json = mapper.writeValueAsString(entity);
        fields.replace(DSL.field("value"), DSL.val(JSONB.valueOf(json)));

        InsertOnDuplicateSetMoreStep<Record> insert = dslContext.insertInto(table)
            .set(DSL.field(DSL.quotedName("key")), queueService.key(entity))
            .set(fields)
            .onConflict(DSL.field(DSL.quotedName("key")))
            .doUpdate()
            .set(fields);

        insert.execute();
    }

    @SuppressWarnings("unchecked")
    public <R extends Record, E> ArrayListTotal<E> fetchPage(SelectConditionStep<R> select, Pageable pageable, RecordMapper<R, E> mapper) {
        Result<Record> results = this.limit(
            this.dslContext.select(DSL.asterisk(), DSL.count().over().as("total_count"))
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
