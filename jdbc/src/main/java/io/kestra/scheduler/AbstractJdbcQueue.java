package io.kestra.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.utils.Disposable;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.jdbc.JdbcMapper;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcQueueConfiguration;
import io.kestra.jdbc.runner.JdbcQueuePoller;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertSetMoreStep;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AbstractJdbcQueue<T> implements JdbcQueue {
    
    private static final Logger log = LoggerFactory.getLogger(AbstractJdbcQueue.class);
    
    private static final ObjectMapper MAPPER = JdbcMapper.of();
    
    // Columns
    protected static final Field<Object> KEY_FIELD = DSL.field(DSL.quotedName("key"));
    protected static final Field<Object> VALUE_FIELD = DSL.field(DSL.quotedName("value"));
    protected static final Field<Object> VNODE_FIELD = DSL.field(DSL.quotedName("vnode"));
    protected static final Field<Long> OFFSET_FIELD = DSL.field(DSL.quotedName("offset"), Long.class);
    
    protected final JdbcQueueConfiguration jdbcQueueConfiguration;
    protected final JooqDSLContextWrapper dslContextWrapper;
    
    protected final Table<Record> table;
    protected final Class<T> eventType;
    
    private final ExecutorService executor;
    protected final JdbcQueueOffsetManager offsetManager;
    
    private final ConcurrentHashMap<Object, JdbcQueuePoller> subscriptions = new ConcurrentHashMap<>();
    
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    @Inject
    public AbstractJdbcQueue(JooqDSLContextWrapper dslContextWrapper,
                             JdbcQueueOffsetManager offsetManager,
                             JdbcQueueConfiguration jdbcQueueConfiguration,
                             ExecutorsUtils executorsUtils,
                             String table,
                             Class<T> eventType) {
        
        this.table = DSL.table(table);
        this.eventType = eventType;
        this.dslContextWrapper = dslContextWrapper;
        this.jdbcQueueConfiguration = jdbcQueueConfiguration;
        this.executor = executorsUtils.cachedThreadPool("jdbc-queue-poller");
        this.offsetManager = offsetManager;
    }
    
    protected void send(T event) {
        send(null, null, event);
    }
    
    protected void send(String key, T event) {
        send(key, null, event);
    }
    
    protected void send(String key, Integer vnode, final T event) {
        Objects.requireNonNull(event, "event must not be null");
        final JSONB value = mapToJSONB(event);
        dslContextWrapper.transaction(configuration -> {
            try {
                DSLContext ctx = DSL.using(configuration);
                InsertSetMoreStep<Record> insert = ctx.insertInto(table)
                    .set(KEY_FIELD, key)
                    .set(VALUE_FIELD, value);
                if (vnode != null) {
                    insert = insert.set(VNODE_FIELD, vnode);
                }
                int inserted = insert.execute();
                log.trace("Inserted {} row(s) into {}", inserted, table.getName());
            } catch (DataAccessException e) {
                throw new KestraRuntimeException("Failed to insert rows into " + table.getName(), e);
            }
        });
    }
    
    protected static JSONB mapToJSONB(Object entity) {
        try {
            return JSONB.valueOf(new String(MAPPER.writeValueAsBytes(entity)));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    
    protected static <T> List<Either<T, DeserializationException>> mapToEntities(Result<Record> fetch, Class<T> type) {
        return fetch
            .map(record -> {
                try {
                    return Either.left(MAPPER.readValue(record.get("value", String.class), type));
                } catch (JsonProcessingException e) {
                    return Either.right(new DeserializationException(e, record.get("value", String.class)));
                }
            });
    }
    
    protected Disposable startPollingTask(final Callable<Integer> pollTask) {
        final Object lease = new Object();
        JdbcQueuePoller poller = new JdbcQueuePoller(jdbcQueueConfiguration, pollTask);
        subscriptions.put(lease, poller);
        executor.execute(poller);
        return Disposable.of(() ->
            Optional.ofNullable(subscriptions.remove(lease)).ifPresent(JdbcQueuePoller::stop)
        );
    }
    
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public void close() {
        if (!this.closed.compareAndSet(true, false)) {
            return; // already stopped
        }
        
        subscriptions.values().forEach(JdbcQueuePoller::stop);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for executor to shut down");
        }
    }
}
