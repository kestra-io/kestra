package io.kestra.runner.postgres;

import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.kestra.jdbc.runner.JdbcQueue;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.NonNull;
import lombok.SneakyThrows;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;

import static org.jooq.impl.DSL.*;

public class PostgresQueue<T> extends JdbcQueue<T> {
    private boolean disableSeqScan = false;

    public PostgresQueue(Class<T> cls, ApplicationContext applicationContext) {
        super(cls, applicationContext);

        var maybeDisableSeScan = applicationContext.getProperty("kestra.queue.postgres.disable-seq-scan", Boolean.class);
        if (maybeDisableSeScan.isPresent() && maybeDisableSeScan.get()) {
            disableSeqScan = true;
        }
    }

    @Override
    @SneakyThrows
    protected Map<Field<Object>, Object> produceFields(String consumerGroup, String key, T message) {
        Map<Field<Object>, Object> map = super.produceFields(consumerGroup, key, message);

        map.put(
            AbstractJdbcRepository.field("type"),
            DSL.field("CAST(? AS queue_type)", this.cls.getName())
        );

        return map;
    }

    @Override
    protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup, @NonNull Integer offset, boolean forUpdate) {
        var select = ctx.select(
                AbstractJdbcRepository.field("value"),
                AbstractJdbcRepository.field("offset")
            )
            .from(this.table)
            .where(DSL.condition("type = CAST(? AS queue_type)", this.cls.getName()));

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
        if (disableSeqScan) {
            ctx.setLocal(name("enable_seqscan"), val("off")).execute();
        }

        var select = ctx.select(
                AbstractJdbcRepository.field("value"),
                AbstractJdbcRepository.field("offset")
            )
            .from(this.table)
            .where(DSL.condition("type = CAST(? AS queue_type)", this.cls.getName()))
            .and(AbstractJdbcRepository.field("consumer_" + queueType, Boolean.class).isFalse());

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
        var update = ctx.update(DSL.table(table.getName()))
            .set(
                AbstractJdbcRepository.field("consumer_" + queueType),
                true
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
