package io.kestra.runner.postgres;

import io.kestra.jdbc.repository.AbstractRepository;
import io.kestra.jdbc.runner.JdbcQueue;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.NonNull;
import lombok.SneakyThrows;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        return ctx
            .resultQuery(
                "SELECT" + "\n" +
                    "  \"value\"," + "\n" +
                    "  \"offset\"" + "\n" +
                    "FROM " + table.getName() + "\n" +
                    "WHERE 1 = 1" + "\n" +
                    (offset != 0 ? "AND \"offset\" > ?" + "\n" : "") +
                    "AND type = CAST(? AS queue_type)" + "\n" +
                    "ORDER BY \"offset\" ASC" + "\n" +
                    "LIMIT 10" + "\n" +
                    "FOR UPDATE SKIP LOCKED",
                offset != 0 ? offset : this.cls.getName(),
                this.cls.getName()
            )
            .fetch();
    }

    protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup) {
        return ctx
            .resultQuery(
                "SELECT" + "\n" +
                    "  \"value\"," + "\n" +
                    "  \"offset\"" + "\n" +
                    "FROM " + table.getName() + "\n" +
                    "WHERE (" +
                    "  \"consumers\" IS NULL" + "\n" +
                    "  OR NOT(CAST(? AS queue_consumers) = ANY(\"consumers\"))" + "\n" +
                    ")" + "\n" +
                    "AND type = CAST(? AS queue_type)" + "\n" +
                    "ORDER BY \"offset\" ASC" + "\n" +
                    "LIMIT 10" + "\n" +
                    "FOR UPDATE SKIP LOCKED",
                consumerGroup,
                this.cls.getName()
            )
            .fetch();
    }

    @Override
    protected void updateGroupOffsets(DSLContext ctx, String consumerGroup, List<Integer> offsets) {
        ctx
            .query(
                "UPDATE " + table.getName() + "\n" +
                    "SET \"consumers\" = \"consumers\" || '{" + consumerGroup + "}' \n" +
                    "WHERE \"offset\" IN (" + offsets.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(",")) + ")",
                consumerGroup
            )
            .execute();
    }
}
