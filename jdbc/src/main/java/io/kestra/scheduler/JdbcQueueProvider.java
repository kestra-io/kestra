package io.kestra.scheduler;

import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.jdbc.JooqDSLContextWrapper;
import io.kestra.jdbc.runner.JdbcQueueConfiguration;
import io.kestra.jdbc.runner.JdbcQueueEnabled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
@JdbcQueueEnabled
public class JdbcQueueProvider implements AutoCloseable {
    
    private final JdbcQueueConfiguration jdbcQueueConfiguration;
    private final JooqDSLContextWrapper dslContextWrapper;
    
    private final ExecutorsUtils executorsUtils;
    private final JdbcQueueOffsetManager offsetManager;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    private final Map<String, JdbcQueue> queuesByName = new HashMap<>();
    
    @Inject
    public JdbcQueueProvider(JooqDSLContextWrapper dslContextWrapper,
                             JdbcQueueOffsetManager offsetManager,
                             JdbcQueueConfiguration jdbcQueueConfiguration,
                             ExecutorsUtils executorsUtils) {
        
        this.dslContextWrapper = dslContextWrapper;
        this.jdbcQueueConfiguration = jdbcQueueConfiguration;
        this.executorsUtils = executorsUtils;
        this.offsetManager = offsetManager;
    }
    
    @SuppressWarnings("unchecked")
    public synchronized <T> JdbcExclusiveVNodeQueue<T> exclusive(String queue, Class<T> type) {
        return (JdbcExclusiveVNodeQueue<T>) queuesByName.computeIfAbsent(queue, (key) -> new JdbcExclusiveVNodeQueue<>(
            dslContextWrapper,
            offsetManager,
            jdbcQueueConfiguration,
            executorsUtils,
            queue,
            type
        ));
    }
    
    @SuppressWarnings("unchecked")
    public synchronized <T> JdbcBroadcastQueue<T> broadcast(String queue, Class<T> type) {
        return (JdbcBroadcastQueue<T>) queuesByName.computeIfAbsent(queue, (key) -> new JdbcBroadcastQueue<>(
            dslContextWrapper,
            offsetManager,
            jdbcQueueConfiguration,
            executorsUtils,
            queue,
            type
        ));
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public void close() throws IOException {
        if (!this.closed.compareAndSet(true, false)) {
            return; // already stopped
        }
        // Make sure all queues are closed
        queuesByName.values().forEach(JdbcQueue::close);
    }
}
