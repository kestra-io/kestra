package io.kestra.scheduler;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.utils.Disposable;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcQueueConfiguration;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class JdbcExclusiveVNodeQueue<T> extends AbstractJdbcQueue<T> {
    
    private static final Logger log = LoggerFactory.getLogger(JdbcExclusiveVNodeQueue.class);
    
    @Inject
    public JdbcExclusiveVNodeQueue(JooqDSLContextWrapper dslContextWrapper,
                                   JdbcQueueOffsetManager offsetManager,
                                   JdbcQueueConfiguration jdbcQueueConfiguration,
                                   ExecutorsUtils executorsUtils,
                                   String table,
                                   Class<T> eventType) {
        super(dslContextWrapper, offsetManager, jdbcQueueConfiguration, executorsUtils, table, eventType);
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public void send(String key, Integer vnode, final T event) {
        Objects.requireNonNull(event, "event must not be null");
        final JSONB value = mapToJSONB(event);
        dslContextWrapper.transaction(configuration -> {
            try {
                DSLContext ctx = DSL.using(configuration);
                InsertSetMoreStep<Record> insert = ctx.insertInto(table)
                    .set(KEY_FIELD, key)
                    .set(VALUE_FIELD, value)
                    .set(VNODE_FIELD, vnode);
                int inserted = insert.execute();
                log.trace("Inserted {} row(s) into {}", inserted, table.getName());
            } catch (DataAccessException e) {
                throw new KestraRuntimeException("Failed to insert rows into " + table.getName(), e);
            }
        });
    }
    
    public synchronized Disposable subscribe(Set<Integer> vNodes, BiConsumer<Integer, List<Either<T, DeserializationException>>> handler) {
        
        // fetch last consumed offset for each vNode
        final Map<Integer, Long> lastOffsetsForVNodes = vNodes.stream()
            .map(vNode -> Map.entry(vNode, offsetManager.fetchEarliestOffset(table.getName(), vNode)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        
        // The callable executed by the poller
        Callable<Integer> pollTask = () -> {
            AtomicInteger consumed = new AtomicInteger();
            dslContextWrapper.transaction(configuration -> {
                DSLContext ctx = DSL.using(configuration);
                
                for (Integer vNode : vNodes) {
                    // Fetch next unprocessed event
                    Result<Record> result = ctx.select(KEY_FIELD, VALUE_FIELD, VNODE_FIELD, OFFSET_FIELD)
                        .from(table)
                        .where(VNODE_FIELD.eq(vNode))
                        .and(OFFSET_FIELD.gt(lastOffsetsForVNodes.get(vNode)))
                        .orderBy(OFFSET_FIELD.asc())
                        .limit(jdbcQueueConfiguration.pollSize())
                        .forUpdate()
                        .skipLocked()
                        .fetchMany()
                        .getFirst();
                    
                    if (!result.isEmpty()) {
                        // Process events
                        handler.accept(vNode, mapToEntities(result, eventType));
                        
                        // Resolve last consumed offset
                        Long lastOffset = result.map(record -> record.get(OFFSET_FIELD)).getLast();
                        lastOffsetsForVNodes.put(vNode, lastOffset);
                        
                        // Remove all records with an offset <= LAST_OFFSET
                        ctx.delete(table).where(OFFSET_FIELD.le(lastOffset)).execute();
                    }
                    consumed.addAndGet(result.size());
                }
            });
            return consumed.get();
        };
        
        return startPollingTask(pollTask);
    }
}
