package org.floworc.core.queues.types;

import lombok.extern.slf4j.Slf4j;
import org.floworc.core.queues.QueueInterface;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Slf4j
public class LocalQueue <T> implements QueueInterface<T> {
    private Class<T> cls;
    private static ExecutorService poolExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private Map<String, List<Consumer<T>>> consumers = new HashMap<>();

    public LocalQueue(Class<T> cls) {
        this.cls = cls;
    }

    @Override
    public void emit(T message) {
        if (log.isTraceEnabled()) {
            log.trace("New message: topic '{}', value {}", this.cls.getName(), message);
        }

        this.consumers
            .forEach((consumerGroup, consumers) -> {
                poolExecutor.execute(() -> {
                    consumers.get((new Random()).nextInt(consumers.size())).accept(message);
                });

            });
    }

    @Override
    public synchronized void receive(Class consumerGroup, Consumer<T> consumer) {
        if (!this.consumers.containsKey(consumerGroup.getName())) {
            this.consumers.put(consumerGroup.getName(), new ArrayList<>());
        }

        this.consumers.get(consumerGroup.getName()).add(consumer);
    }

    public int getSubscribersCount() {
        return this.consumers
            .values()
            .stream()
            .map(List::size)
            .reduce(0, Integer::sum);
    }

    @Override
    public void ack(T message) {
        // no ack needed with local queues
    }
}
