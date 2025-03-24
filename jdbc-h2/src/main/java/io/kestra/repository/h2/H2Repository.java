package io.kestra.repository.h2;

import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ArrayListTotal;
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
import org.jooq.LikeEscapeStep;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import jakarta.annotation.Nullable;

@H2RepositoryEnabled
@EachBean(JdbcTableConfig.class)
public class H2Repository<T> extends io.kestra.jdbc.AbstractJdbcRepository<T> {

    @Inject
    public H2Repository(@Parameter JdbcTableConfig jdbcTableConfig,
                        QueueService queueService,
                        JooqDSLContextWrapper dslContextWrapper) {
        super(jdbcTableConfig, queueService, dslContextWrapper);
    }

    @Override
    @SneakyThrows
    public void persist(T entity, DSLContext context, @Nullable Map<Field<Object>, Object> fields) {
        Map<Field<Object>, Object> finalFields = fields == null ? this.persistFields(entity) : fields;

        this.persistInternal(entity, context, finalFields);
    }

    private int persistInternal(T entity, DSLContext context, Map<Field<Object>, Object> fields) {
        int affectedRows = context
            .update(table)
            .set(fields)
            .where(AbstractJdbcRepository.field("key").eq(key(entity)))
            .execute();

        if (affectedRows == 0) {
           return  context
                .insertInto(table)
                .set(AbstractJdbcRepository.field("key"), key(entity))
                .set(fields)
                .execute();
        } else {
            return affectedRows;
        }
    }

    @Override
    public int persistBatch(List<T> items) {
        return dslContextWrapper.transactionResult(configuration -> {
            DSLContext dslContext = DSL.using(configuration);
            return items.stream()
                .map(item -> this.persistInternal(item, dslContext, this.persistFields(item)))
                .mapToInt(i -> i)
                .sum();
        });
    }

    public Condition fullTextCondition(List<String> fields, String query) {
        if (query == null || query.equals("*")) {
            return DSL.trueCondition();
        }

        if (fields.size() > 1) {
            throw new IllegalStateException("Too many fields for h2 '" + fields + "'");
        }

        Field<Object> field = AbstractJdbcRepository.field(fields.getFirst());

        List<LikeEscapeStep> match = Arrays
            .stream(query.split("\\p{P}|\\p{S}|\\p{Z}"))
            .map(s -> field.likeIgnoreCase("%" + s.toUpperCase(Locale.ROOT) + "%"))
            .toList();

        if (match.size() == 0) {
            return DSL.falseCondition();
        }

        return DSL.and(match);
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

        Integer totalCount = results.size() > 0 ? results.getFirst().get("total_count", Integer.class) : 0;

        List<E> map = results
            .map((Record record) -> mapper.map((R) record));

        return new ArrayListTotal<>(map, totalCount);
    }
}
