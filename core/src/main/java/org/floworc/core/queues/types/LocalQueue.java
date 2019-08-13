package org.floworc.core.queues.types;

import lombok.extern.slf4j.Slf4j;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.queues.QueueMessage;
import org.floworc.core.queues.QueueName;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

@Slf4j
public class LocalQueue <T> implements QueueInterface<T> {
    private static ThreadPoolExecutor poolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private QueueName topic;
    private List<QueueMessage<T>> messages = new ArrayList<>();
    private List<Consumer<QueueMessage<T>>> consumers = new ArrayList<>();

    public LocalQueue(QueueName topic) {
        this.topic = topic;
    }

    @Override
    public boolean emit(QueueMessage<T> message) {
        if (log.isTraceEnabled()) {
            log.trace("New message: topic '{}', key '{}', value {}", this.topic, message.getKey(), message.getBody());
        }

        this.messages.add(message);

       if (this.consumers != null) {
            if (this.topic.isPubSub()) {
                this.consumers
                    .forEach(consumers ->
                        poolExecutor.execute(() ->
                            consumers.accept(message)
                        )
                    );
            } else {
                poolExecutor.execute(() -> {
                    this.consumers.get((new Random()).nextInt(this.consumers.size())).accept(message);
                });
            }
        }

        return true;
    }

    @Override
    public void receive(Consumer<QueueMessage<T>> consumer) {
        this.consumers.add(consumer);
    }

    @Override
    public void ack(QueueMessage<T> message) {
        this.messages.remove(message);
    }
}
