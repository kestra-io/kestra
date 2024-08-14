package io.kestra.runner.mysql;

import io.kestra.jdbc.repository.AbstractJdbcRepository;
import io.kestra.jdbc.runner.JdbcQueue;
import io.micronaut.context.ApplicationContext;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MysqlQueue<T> extends JdbcQueue<T> {

    // TODO - remove once 'queue' table is re-designed
    private static final MysqlQueueConsumers QUEUE_CONSUMERS = new MysqlQueueConsumers();

    public MysqlQueue(Class<T> cls, ApplicationContext applicationContext) {
        super(cls, applicationContext);
    }

    @Override
    protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup, Integer offset) {
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

        return select
            .orderBy(AbstractJdbcRepository.field("offset").asc())
            .limit(configuration.getPollSize())
            .forUpdate()
            .skipLocked()
            .fetchMany()
            .get(0);
    }

    @Override
    protected Result<Record> receiveFetch(DSLContext ctx, String consumerGroup, String queueType) {
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
                AbstractJdbcRepository.field("consumers").in(QUEUE_CONSUMERS.allForConsumerNotIn(queueType))
            )));

        if (consumerGroup != null) {
            select = select.and(AbstractJdbcRepository.field("consumer_group").eq(consumerGroup));
        } else {
            select = select.and(AbstractJdbcRepository.field("consumer_group").isNull());
        }

        return select.orderBy(AbstractJdbcRepository.field("offset").asc())
            .limit(configuration.getPollSize())
            .forUpdate()
            .skipLocked()
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

    private static final class MysqlQueueConsumers {

        private static final Set<String> CONSUMERS;

        static {
            CONSUMERS = new HashSet<>();
            String[] elements = {"indexer", "executor", "worker", "scheduler"};
            List<String> results = new ArrayList<>();
            // Generate all combinations and their permutations
            generateCombinations(elements, new boolean[elements.length], new ArrayList<>(), results);
            CONSUMERS.addAll(results);
        }

        public Set<String> allForConsumerNotIn(String consumer) {
            return CONSUMERS.stream().filter(s -> !s.contains(consumer)).collect(Collectors.toSet());
        }

        private static void generateCombinations(String[] elements, boolean[] used, List<String> current, List<String> results) {
            if (!current.isEmpty()) {
                results.add(String.join(",", current));
            }

            for (int i = 0; i < elements.length; i++) {
                if (!used[i]) {
                    used[i] = true;
                    current.add(elements[i]);
                    generateCombinations(elements, used, current, results);
                    current.removeLast();
                    used[i] = false;
                }
            }
        }
    }
}
