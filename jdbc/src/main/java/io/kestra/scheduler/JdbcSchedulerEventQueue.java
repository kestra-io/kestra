package io.kestra.scheduler;

import io.kestra.core.scheduler.SchedulerEventQueue;
import io.kestra.core.utils.Disposable;
import io.kestra.jdbc.runner.JdbcQueueEnabled;
import io.kestra.core.scheduler.events.SchedulerEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

@Singleton
@JdbcQueueEnabled
public class JdbcSchedulerEventQueue implements SchedulerEventQueue {
    
    private static final Logger log = LoggerFactory.getLogger(JdbcSchedulerEventQueue.class);
    
    // Tables
    private static final String QUEUE_TABLE_NAME = "queue_scheduler_event";
    
    private final JdbcBroadcastQueue<SchedulerEvent> queue;
    
    @Inject
    public JdbcSchedulerEventQueue(JdbcQueueProvider jdbcQueueProvider) {
        this.queue = jdbcQueueProvider.broadcast(QUEUE_TABLE_NAME, SchedulerEvent.class);
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public void send(SchedulerEvent event) {
        this.queue.send(event);
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public Disposable subscribe(Consumer<SchedulerEvent> consumer) {
        return queue.subscribe( records -> {
            records.stream().map(either -> either.fold(
                    Function.identity(),
                    e -> {
                        log.warn("Failed to deserialize event. Cause: {}", e.getMessage());
                        return null;
                    }
                ))
                .filter(Objects::nonNull)
                .forEach(consumer);
        });
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public void close() {
       // queue is managed by the provider
    }
}
