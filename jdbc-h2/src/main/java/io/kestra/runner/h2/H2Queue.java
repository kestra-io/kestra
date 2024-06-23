package io.kestra.runner.h2;

import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.kestra.jdbc.runner.JdbcQueue;
import io.micronaut.context.ApplicationContext;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.util.List;

public class H2Queue<T> extends JdbcQueue<T> {
    public H2Queue(Class<T> cls, ApplicationContext applicationContext) {
        super(cls, applicationContext);
    }

    @Override
    protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup, Integer offset, boolean forUpdate) {
        var select = ctx.select(
                AbstractJdbcRepository.field("value"),
                AbstractJdbcRepository.field("offset")
            )
            .from(this.table)
            .where(AbstractJdbcRepository.field("type").eq(this.cls.getName()));

        if (consumerGroup != null) {
            select = select.and(AbstractJdbcRepository.field("consumer_group").eq(consumerGroup));
        } else {
            select = select.and(AbstractJdbcRepository.field("consumer_group").isNull());
        }

        if (offset != 0) {
            select = select.and(AbstractJdbcRepository.field("offset").gt(offset));
        }

        var limitSelect = select
            .orderBy(AbstractJdbcRepository.field("offset").asc())
            .limit(configuration.getPollSize());
        ResultQuery<Record2<Object, Object>> configuredSelect = limitSelect;

        if (forUpdate) {
            configuredSelect = limitSelect.forUpdate().skipLocked();
        }

        return configuredSelect
            .fetchMany()
            .getFirst();
    }

    @Override
    protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup, String queueType, boolean forUpdate) {
        var select =  ctx.select(
                AbstractJdbcRepository.field("value"),
                AbstractJdbcRepository.field("offset")
            )
            .from(this.table)
            .where(AbstractJdbcRepository.field("type").eq(this.cls.getName()))
            .and(DSL.or(List.of(
                AbstractJdbcRepository.field("consumers").isNull(),
                DSL.condition("NOT(ARRAY_CONTAINS(\"consumers\", ?))", queueType)
            )));

        if (consumerGroup != null) {
            select = select.and(AbstractJdbcRepository.field("consumer_group").eq(consumerGroup));
        } else {
            select = select.and(AbstractJdbcRepository.field("consumer_group").isNull());
        }

        var limitSelect = select
            .orderBy(AbstractJdbcRepository.field("offset").asc())
            .limit(configuration.getPollSize());
        ResultQuery<Record2<Object, Object>> configuredSelect = limitSelect;

        if (forUpdate) {
            configuredSelect = limitSelect.forUpdate().skipLocked();
        }

        return configuredSelect
            .fetchMany()
            .getFirst();
    }

    @SuppressWarnings("RedundantCast")
    @Override
    protected void updateGroupOffsets(DSLContext ctx, String consumerGroup, String queueType, List<Integer> offsets) {
        var update = ctx.update(DSL.table(table.getName()))
            .set(
                AbstractJdbcRepository.field("consumers"),
                DSL.field(
                    "ARRAY_APPEND(COALESCE(\"consumers\", ARRAY[]), ?)",
                    SQLDataType.VARCHAR(50).getArrayType(),
                    (Object) new String[]{queueType}
                )
            )
            .where(AbstractJdbcRepository.field("offset").in((Object[]) offsets.toArray(Integer[]::new)));

        if (consumerGroup != null) {
            update = update.and(AbstractJdbcRepository.field("consumer_group").eq(consumerGroup));
        } else {
            update = update.and(AbstractJdbcRepository.field("consumer_group").isNull());
        }

        update.execute();
    }
}
