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
    protected Result<Record> receiveFetch(DSLContext ctx, Integer offset) {
        SelectConditionStep<Record2<Object, Object>> select = ctx
            .select(
                AbstractJdbcRepository.field("value"),
                AbstractJdbcRepository.field("offset")
            )
            .from(this.table)
            .where(AbstractJdbcRepository.field("type").eq(this.cls.getName()));

        if (offset != 0) {
            select = select.and(AbstractJdbcRepository.field("offset").gt(offset));
        }

        return select
            .orderBy(AbstractJdbcRepository.field("offset").asc())
            .limit(10)
            .forUpdate()
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
            .where(AbstractJdbcRepository.field("type").eq(this.cls.getName()))
            .and(DSL.or(List.of(
                AbstractJdbcRepository.field("consumers").isNull(),
                DSL.condition("NOT(ARRAY_CONTAINS(\"consumers\", ?))", consumerGroup)
            )))
            .orderBy(AbstractJdbcRepository.field("offset").asc())
            .limit(10)
            .forUpdate()
            .fetchMany()
            .get(0);
    }

    @Override
    protected void updateGroupOffsets(DSLContext ctx, String consumerGroup, List<Integer> offsets) {
        ctx
            .update(DSL.table(table.getName()))
            .set(
                AbstractJdbcRepository.field("consumers"),
                DSL.field(
                    "ARRAY_APPEND(COALESCE(\"consumers\", ARRAY[]), ?)",
                    SQLDataType.VARCHAR(50).getArrayType(),
                    (Object) new String[]{consumerGroup}
                )
            )
            .where(AbstractJdbcRepository.field("offset").in(offsets.toArray(Integer[]::new)))
            .execute();
    }
}
