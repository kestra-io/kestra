package io.kestra.runner.h2;

import io.kestra.jdbc.runner.JdbcQueue;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.NonNull;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;

import java.util.List;
import java.util.stream.Collectors;

public class H2Queue<T> extends JdbcQueue<T> {
    public H2Queue(Class<T> cls, ApplicationContext applicationContext) {
        super(cls, applicationContext);
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
                    "AND \"type\" = ? " + "\n" +
                    "ORDER BY \"offset\" ASC" + "\n" +
                    "LIMIT 10" + "\n" +
                    "FOR UPDATE",
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
                    "  OR NOT(ARRAY_CONTAINS(\"consumers\", ?))" + "\n" +
                    ")" + "\n" +
                    "AND \"type\" = ?" + "\n" +
                    "ORDER BY \"offset\" ASC" + "\n" +
                    "LIMIT 10" + "\n" +
                    "FOR UPDATE",
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
                    "SET \"consumers\" = COALESCE(\"consumers\", ARRAY[]) || ARRAY['" + consumerGroup + "']\n" +
                    "WHERE \"offset\" IN (" +
                    offsets
                        .stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(",")) +
                    ")",
                consumerGroup
            )
            .execute();
    }
}
