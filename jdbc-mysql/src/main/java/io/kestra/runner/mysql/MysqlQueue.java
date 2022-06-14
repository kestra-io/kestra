package io.kestra.runner.mysql;

import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.kestra.jdbc.runner.JdbcQueue;
import io.micronaut.context.ApplicationContext;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.List;

public class MysqlQueue<T> extends JdbcQueue<T> {
    public MysqlQueue(Class<T> cls, ApplicationContext applicationContext) {
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
            .where(AbstractJdbcRepository.field("type").eq(this.cls.getName()))
            .and(DSL.or(List.of(
                AbstractJdbcRepository.field("consumers").isNull(),
                DSL.condition("NOT(FIND_IN_SET(?, consumers) > 0)", consumerGroup)
            )))
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
                AbstractJdbcRepository.field("consumers"),
                DSL.field("CONCAT_WS(',', consumers, ?)", String.class, consumerGroup)
            )
            .where(AbstractJdbcRepository.field("offset").in(offsets.toArray(Integer[]::new)))
            .execute();
    }
}
