package org.kestra.runner.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.serializers.JacksonMapper;
import org.kestra.core.utils.ThreadMainFactoryBuilder;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Slf4j
public class MemoryQueue<T> implements QueueInterface<T> {
    private static final ObjectMapper mapper = JacksonMapper.ofJson();
    private static ExecutorService poolExecutor;

    private final Class<T> cls;
    private final Map<String, List<Consumer<T>>> consumers = new ConcurrentHashMap<>();

    public MemoryQueue(Class<T> cls, ApplicationContext applicationContext) {
        if (poolExecutor == null) {
            poolExecutor = Executors.newCachedThreadPool(
                applicationContext.getBean(ThreadMainFactoryBuilder.class).build("memory-queue-%d")
            );
        }

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
                    if (consumers.size() == 0) {
                        log.debug("No consumer connected on queue '" + this.cls.getName() + "'");
                        return;
                    }

                    // we force serialization to be a the same case than an another queue with serialization
                    // this enabled debugging classLoader
                    try {
                        T serialized = mapper.readValue(mapper.writeValueAsString(message), this.cls);
                        int i = (new Random()).nextInt(consumers.size());
                        consumers.get(i).accept(serialized);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
            });
    }

    @Override
    public Runnable receive(Consumer<T> consumer) {
        return this.receive(null, consumer);
    }

    @Override
    public synchronized Runnable receive(Class<?> consumerGroup, Consumer<T> consumer) {
        String consumerGroupName;
        if (consumerGroup == null) {
            consumerGroupName = UUID.randomUUID().toString();
        } else {
            consumerGroupName = consumerGroup.getSimpleName();
        }

        if (!this.consumers.containsKey(consumerGroupName)) {
            this.consumers.put(consumerGroupName, new ArrayList<>());
        }

        this.consumers.get(consumerGroupName).add(consumer);
        int index = this.consumers.get(consumerGroupName).size() - 1;

        return () -> {
            synchronized (this) {
                this.consumers.get(consumerGroupName).remove(index);

                if (this.consumers.get(consumerGroupName).size() == 0) {
                    this.consumers.remove(consumerGroupName);
                }
            }
        };
    }

    public int getSubscribersCount() {
        return this.consumers
            .values()
            .stream()
            .map(List::size)
            .reduce(0, Integer::sum);
    }

    @Override
    public void close() throws IOException {
        if (!poolExecutor.isShutdown()) {
            poolExecutor.shutdown();
        }
    }
}
