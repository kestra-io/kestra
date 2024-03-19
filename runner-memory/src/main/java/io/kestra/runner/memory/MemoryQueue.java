package io.kestra.runner.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.Hashing;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.utils.Either;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import io.kestra.core.queues.QueueService;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.serializers.JacksonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

@Slf4j
public class MemoryQueue<T> implements QueueInterface<T> {
    private static final ObjectMapper mapper = JacksonMapper.ofJson();

    private final ExecutorService executorService;

    private final QueueService queueService;

    private final Class<T> cls;
    private final Map<String, List<Consumer<Either<T, DeserializationException>>>> queues = new ConcurrentHashMap<>();

    public MemoryQueue(final Class<T> cls,
                       final QueueService queueService,
                       final ExecutorService executorService) {
        this.executorService = executorService;
        this.queueService = queueService;
        this.cls = cls;
    }

    @SuppressWarnings("UnstableApiUsage")
    private static int selectConsumer(String key, int size) {
        if (key == null) {
            return (new Random()).nextInt(size);
        } else {
            return Hashing.consistentHash(Hashing.crc32().hashString(key, StandardCharsets.UTF_8), size);
        }
    }

    private void produce(String key, T message) {
        if (log.isTraceEnabled()) {
            log.trace("New message: topic '{}', value {}", this.cls.getName(), message);
        }

        this.queues
            .forEach((consumerGroup, consumers) -> {
                executorService.execute(() -> {
                    Consumer<Either<T, DeserializationException>> consumer;

                    synchronized (this) {
                        if (consumers.isEmpty()) {
                            log.debug("No consumer connected on queue '" + this.cls.getName() + "'");
                            return;
                        } else {
                            int index = selectConsumer(key, consumers.size());
                            consumer = consumers.get(index);
                        }
                    }

                    // we force serialization to be at the same case than an another queue implementation with serialization
                    // this enabled debugging classLoader
                    String source = null;
                    try {
                        source = mapper.writeValueAsString(message);
                        T serialized = message == null ? null : mapper.readValue(source, this.cls);
                        consumer.accept(Either.left(serialized));
                    } catch (JsonProcessingException e) {
                        consumer.accept(Either.right(new DeserializationException(e, source)));
                    }
                });
            });
    }

    @Override
    public void emit(String consumerGroup, T message) {
        this.produce(queueService.key(message), message);
    }

    @Override
    public void emitAsync(String consumerGroup, T message) throws QueueException {
        this.emit(message);
    }

    @Override
    public void delete(String consumerGroup, T message) throws QueueException {
        this.produce(queueService.key(message), null);
    }

    @Override
    public Runnable receive(String consumerGroup, Consumer<Either<T, DeserializationException>> consumer) {
        return this.receive(consumerGroup, null, consumer);
    }

    @Override
    public synchronized Runnable receive(String consumerGroup, Class<?> queueType, Consumer<Either<T, DeserializationException>> consumer) {
        String queueName;
        if (queueType == null) {
            queueName = UUID.randomUUID().toString();
        } else {
            queueName = queueType.getSimpleName();
        }

        if (!this.queues.containsKey(queueName)) {
            this.queues.put(queueName, Collections.synchronizedList(new ArrayList<>()));
        }

        this.queues.get(queueName).add(consumer);
        int index = this.queues.get(queueName).size() - 1;

        return () -> {
            synchronized (this) {
                this.queues.get(queueName).remove(index);

                if (this.queues.get(queueName).isEmpty()) {
                    this.queues.remove(queueName);
                }
            }
        };
    }

    @Override
    public void pause() {

    }

    public int getSubscribersCount() {
        return this.queues
            .values()
            .stream()
            .map(List::size)
            .reduce(0, Integer::sum);
    }

    @Override
    public void close() throws IOException {
        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
