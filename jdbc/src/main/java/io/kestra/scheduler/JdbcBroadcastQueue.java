package io.kestra.scheduler;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.utils.Disposable;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcQueueConfiguration;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class JdbcBroadcastQueue<T> extends AbstractJdbcQueue<T> {
    
    @Inject
    public JdbcBroadcastQueue(JooqDSLContextWrapper dslContextWrapper,
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
    public void send(T event) {
        send(null, null, event);
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public void send(String key, T event) {
        super.send(key, event);
    }
    
    public synchronized Disposable subscribe(Consumer<List<Either<T, DeserializationException>>> handler) {
        
        // fetch last consumed offset for each vNode
        final AtomicLong lastOffset = new AtomicLong(offsetManager.fetchLastestOffset(table.getName()));
        
        // The callable executed by the poller
        Callable<Integer> pollTask = () -> {
            AtomicInteger consumed = new AtomicInteger();
            dslContextWrapper.transaction(configuration -> {
                DSLContext ctx = DSL.using(configuration);
                
                // Fetch next unprocessed event
                Result<Record> result = ctx.select(KEY_FIELD, VALUE_FIELD, OFFSET_FIELD)
                    .from(table)
                    .where(OFFSET_FIELD.gt(lastOffset.get()))
                    .orderBy(OFFSET_FIELD.asc())
                    .limit(jdbcQueueConfiguration.pollSize())
                    .forUpdate()
                    .skipLocked()
                    .fetchMany()
                    .getFirst();
                
                if (!result.isEmpty()) {
                    // Process events
                    handler.accept(mapToEntities(result, eventType));
                    
                    // Resolve last consumed offset
                    lastOffset.set(result.map(record -> record.get(OFFSET_FIELD)).getLast());
                }
                
                consumed.addAndGet(result.size());
            });
            return consumed.get();
        };
        return startPollingTask(pollTask);
    }
}
