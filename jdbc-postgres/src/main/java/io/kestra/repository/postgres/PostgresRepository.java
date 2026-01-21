package io.kestra.repository.postgres;

import io.kestra.core.models.HasUID;
import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.jdbc.JdbcTableConfig;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertOnDuplicateSetMoreStep;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.annotation.Nullable;

import static io.kestra.jdbc.repository.AbstractJdbcRepository.KEY_FIELD;
import static io.kestra.jdbc.repository.AbstractJdbcRepository.VALUE_FIELD;

@PostgresRepositoryEnabled
@EachBean(JdbcTableConfig.class)
public class PostgresRepository<T> extends io.kestra.jdbc.AbstractJdbcRepository<T> {

    @Inject
    public PostgresRepository(@Parameter JdbcTableConfig jdbcTableConfig,
                              JooqDSLContextWrapper dslContextWrapper) {
        super(jdbcTableConfig, dslContextWrapper);
    }

    @Override
    public Condition fullTextCondition(List<String> fields, String query) {
        if (query == null || query.equals("*")) {
            return DSL.trueCondition();
        }

        if (fields.size() > 1) {
            throw new IllegalArgumentException("Invalid fullTextCondition" + fields);
        }

        return DSL.condition(fields.getFirst() + " @@ FULLTEXT_SEARCH(?)", query);
    }

    @SneakyThrows
    @Override
    public Map<Field<Object>, Object> persistFields(T entity) {
        String json = MAPPER.writeValueAsString(entity);
        Map<Field<Object>, Object> fields = HashMap.newHashMap(1);
        fields.put(VALUE_FIELD, DSL.val(JSONB.valueOf(json)));
        return fields;
    }

    @SneakyThrows
    @Override
    public void persist(T entity, DSLContext context, @Nullable  Map<Field<Object>, Object> fields) {
        Map<Field<Object>, Object> finalFields = fields == null ? this.persistFields(entity) : fields;

        context
            .insertInto(table)
            .set(KEY_FIELD, key(entity))
            .set(finalFields)
            .onConflict(KEY_FIELD)
            .doUpdate()
            .set(finalFields)
            .execute();
    }

    @Override
    protected InsertOnDuplicateSetMoreStep<Record> buildInsertRequest(T entity, Map<Field<Object>, Object> fields,
        DSLContext dslContext) {

        return dslContext
            .insertInto(table)
            .set(KEY_FIELD, key(entity))
            .set(fields)
            .onConflict(KEY_FIELD)
            .doUpdate()
            .set(fields);
    }

    @SuppressWarnings("unchecked")
    @Override
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

        Integer totalCount = !results.isEmpty() ? results.getFirst().get("total_count", Integer.class) : 0;

        List<E> map = results
            .map((Record record) -> mapper.map((R) record));

        return new ArrayListTotal<>(map, totalCount);
    }

    @Override
    public <R extends Record> T map(R record) {
        if (deserializer != null) {
            return deserializer.apply(record);
        } else {
            return this.deserialize(record.get("value", JSONB.class).data());
        }
    }
}
