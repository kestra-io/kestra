package io.kestra.scheduler;

import io.kestra.scheduler.vnodes.VNodes;
import io.kestra.core.utils.Disposable;
import io.kestra.jdbc.runner.JdbcQueueEnabled;
import io.kestra.scheduler.events.TriggerEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Slf4j
@Singleton
@JdbcQueueEnabled
public class JdbcTriggerEventQueue implements TriggerEventQueue {
    
    // Tables
    private static final String QUEUE_TABLE_NAME = "queue_trigger_event";
    
    private final JdbcExclusiveVNodeQueue<TriggerEvent> queue;
    private final SchedulerConfiguration schedulerConfiguration;
    
    @Inject
    public JdbcTriggerEventQueue(JdbcQueueProvider jdbcQueueProvider,
                                 SchedulerConfiguration schedulerConfiguration) {
        this.queue = jdbcQueueProvider.exclusive(QUEUE_TABLE_NAME, TriggerEvent.class);
        this.schedulerConfiguration = schedulerConfiguration;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void send(final TriggerEvent event) {
        queue.send(event.uid(), VNodes.computeVNodeFromTrigger(event.id(), schedulerConfiguration.vnodes()), event);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Disposable subscribe(Set<Integer> vNodes, BatchRecordConsumer consumer) {
        return queue.subscribe(vNodes, (integer, records) -> {
            List<TriggerEvent> events = records.stream().map(either -> either.fold(
                    Function.identity(),
                    e -> {
                        log.warn("Failed to deserialize event. Cause: {}", e.getMessage());
                        return null;
                    }
                )).filter(Objects::nonNull)
                .toList();
            consumer.accept(integer, events);
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
