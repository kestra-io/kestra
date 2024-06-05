package io.kestra.runner.mysql;

import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.kestra.jdbc.runner.JdbcQueue;
import io.micronaut.context.ApplicationContext;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.util.List;

public class MysqlQueue<T> extends JdbcQueue<T> {
    public MysqlQueue(Class<T> cls, ApplicationContext applicationContext) {
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

        if (offset != 0) {
            select = select.and(AbstractJdbcRepository.field("offset").gt(offset));
        }

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
            .get(0);
    }

    @Override
    protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup, String queueType, boolean forUpdate) {
        var select = ctx
            .select(
                AbstractJdbcRepository.field("value"),
                AbstractJdbcRepository.field("offset")
            )
            // force using the dedicated index, or it made a scan of the PK index
            .from(this.table.useIndex("ix_type__consumers"))
            .where(AbstractJdbcRepository.field("type").eq(this.cls.getName()))
            .and(DSL.or(List.of(
                AbstractJdbcRepository.field("consumers").isNull(),
                DSL.condition("NOT(FIND_IN_SET(?, consumers) > 0)", queueType)
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
            .get(0);
    }

    @SuppressWarnings("RedundantCast")
    @Override
    protected void updateGroupOffsets(DSLContext ctx, String consumerGroup, String queueType, List<Integer> offsets) {
        var update = ctx
            .update(DSL.table(table.getName()))
            .set(
                AbstractJdbcRepository.field("consumers"),
                DSL.field("CONCAT_WS(',', consumers, ?)", String.class, queueType)
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
