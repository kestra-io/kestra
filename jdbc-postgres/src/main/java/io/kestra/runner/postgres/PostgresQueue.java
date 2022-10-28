package io.kestra.runner.postgres;

import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.kestra.jdbc.runner.JdbcQueue;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.NonNull;
import lombok.SneakyThrows;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.util.List;
import java.util.Map;

public class PostgresQueue<T> extends JdbcQueue<T> {
    public PostgresQueue(Class<T> cls, ApplicationContext applicationContext) {
        super(cls, applicationContext);
    }

    @Override
    @SneakyThrows
    protected Map<Field<Object>, Object> produceFields(String key, T message) {
        Map<Field<Object>, Object> map = super.produceFields(key, message);

        map.put(
            AbstractJdbcRepository.field("value"),
            JSONB.valueOf(mapper.writeValueAsString(message))
        );

        map.put(
            AbstractJdbcRepository.field("type"),
            DSL.field("CAST(? AS queue_type)", this.cls.getName())
        );

        return map;
    }

    protected Result<Record> receiveFetch(DSLContext ctx, @NonNull Integer offset) {
        SelectConditionStep<Record2<Object, Object>> select = ctx
            .select(
                AbstractJdbcRepository.field("value"),
                AbstractJdbcRepository.field("offset")
            )
            .from(this.table)
            .where(DSL.condition("type = CAST(? AS queue_type)", this.cls.getName()));

        if (offset != 0) {
            select = select.and(AbstractJdbcRepository.field("offset").gt(offset));
        }

        return select
            .orderBy(AbstractJdbcRepository.field("offset").asc())
            .limit(10)
            .forUpdate()
            .skipLocked()
            .fetchMany()
            .get(0);
    }

    protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup) {
        return ctx
            .select(
                AbstractJdbcRepository.field("value"),
                AbstractJdbcRepository.field("offset")
            )
            .from(this.table)
            .where(DSL.condition("type = CAST(? AS queue_type)", this.cls.getName()))
            .and(AbstractJdbcRepository.field("consumer_" + consumerGroup, Boolean.class).isFalse())
            .orderBy(AbstractJdbcRepository.field("offset").asc())
            .limit(10)
            .forUpdate()
            .skipLocked()
            .fetchMany()
            .get(0);
    }

    @Override
    protected void updateGroupOffsets(DSLContext ctx, String consumerGroup, List<Integer> offsets) {
        ctx
            .update(DSL.table(table.getName()))
            .set(
                AbstractJdbcRepository.field("consumer_" + consumerGroup),
                true
            )
            .where(AbstractJdbcRepository.field("offset").in(offsets.toArray(Integer[]::new)))
            .execute();
    }
}
