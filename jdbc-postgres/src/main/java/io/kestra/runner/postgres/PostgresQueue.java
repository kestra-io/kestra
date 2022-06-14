package io.kestra.runner.postgres;

import io.kestra.jdbc.repository.AbstractRepository;
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
            AbstractRepository.field("value"),
            JSONB.valueOf(mapper.writeValueAsString(message))
        );

        map.put(
            AbstractRepository.field("type"),
            DSL.field("CAST(? AS queue_type)", this.cls.getName())
        );

        return map;
    }

    protected Result<Record> receiveFetch(DSLContext ctx, @NonNull Integer offset) {
        SelectConditionStep<Record2<Object, Object>> select = ctx
            .select(
                AbstractRepository.field("value"),
                AbstractRepository.field("offset")
            )
            .from(this.table)
            .where(DSL.condition("type = CAST(? AS queue_type)", this.cls.getName()));

        if (offset != 0) {
            select = select.and(AbstractRepository.field("offset").gt(offset));
        }

        return select
            .orderBy(AbstractRepository.field("offset").asc())
            .limit(10)
            .forUpdate()
            .skipLocked()
            .fetchMany()
            .get(0);
    }

    protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup) {
        return ctx
            .select(
                AbstractRepository.field("value"),
                AbstractRepository.field("offset")
            )
            .from(this.table)
            .where(DSL.condition("type = CAST(? AS queue_type)", this.cls.getName()))
            .and(DSL.or(List.of(
                AbstractRepository.field("consumers").isNull(),
                DSL.condition("NOT(CAST(? AS queue_consumers) = ANY(\"consumers\"))", consumerGroup)
            )))
            .orderBy(AbstractRepository.field("offset").asc())
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
                AbstractRepository.field("consumers"),
                DSL.field(
                    "\"consumers\" || CAST(? AS queue_consumers[])",
                    SQLDataType.VARCHAR(50).getArrayType(),
                    (Object) new String[]{consumerGroup}
                )
            )
            .where(AbstractRepository.field("offset").in(offsets.toArray(Integer[]::new)))
            .execute();
    }
}
