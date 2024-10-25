package io.kestra.repository.postgres;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.JdbcTableConfig;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.annotation.Nullable;

@PostgresRepositoryEnabled
@EachBean(JdbcTableConfig.class)
public class PostgresRepository<T> extends io.kestra.jdbc.AbstractJdbcRepository<T> {

    @Inject
    public PostgresRepository(@Parameter JdbcTableConfig jdbcTableConfig,
                              QueueService queueService,
                              JooqDSLContextWrapper dslContextWrapper) {
        super(jdbcTableConfig, queueService, dslContextWrapper);
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
        String json = JdbcMapper.of().writeValueAsString(entity);
        return new HashMap<>(ImmutableMap
            .of(io.kestra.jdbc.repository.AbstractJdbcRepository.field("value"), DSL.val(JSONB.valueOf(json)))
        );
    }

    @SneakyThrows
    @Override
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

    @Override
    public int persistBatch(List<T> items) {
        return dslContextWrapper.transactionResult(configuration -> {
            DSLContext dslContext = DSL.using(configuration);
            var inserts = items.stream().map(item -> {
                    Map<Field<Object>, Object> finalFields = this.persistFields(item);

                    return dslContext
                        .insertInto(table)
                        .set(AbstractJdbcRepository.field("key"), key(item))
                        .set(finalFields)
                        .onConflict(AbstractJdbcRepository.field("key"))
                        .doUpdate()
                        .set(finalFields);
                })
                .toList();

            return Arrays.stream(dslContext.batch(inserts).execute()).sum();
        });
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

        Integer totalCount = results.size() > 0 ? results.getFirst().get("total_count", Integer.class) : 0;

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
