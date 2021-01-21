package org.kestra.runner.kafka;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.kestra.core.queues.AbstractQueue;
import org.kestra.core.queues.QueueException;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.utils.ExecutorsUtils;
import org.kestra.runner.kafka.configs.TopicsConfig;
import org.kestra.runner.kafka.serializers.JsonSerde;
import org.kestra.runner.kafka.services.KafkaAdminService;
import org.kestra.runner.kafka.services.KafkaConsumerService;
import org.kestra.runner.kafka.services.KafkaProducerService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;

@Slf4j
public class KafkaQueue<T> extends AbstractQueue implements QueueInterface<T>, AutoCloseable {
    private Class<T> cls;
    private final AdminClient adminClient;
    private final KafkaConsumerService kafkaConsumerService;
    private final List<org.apache.kafka.clients.consumer.Consumer<String, T>> kafkaConsumers = new ArrayList<>();
    private static ExecutorService poolExecutor;

    private KafkaProducer<String, T> kafkaProducer;
    private TopicsConfig topicsConfig;

    private KafkaQueue(ApplicationContext applicationContext) {
        if (poolExecutor == null) {
            ExecutorsUtils executorsUtils = applicationContext.getBean(ExecutorsUtils.class);
            poolExecutor = executorsUtils.cachedThreadPool("kakfa-queue");
        }

        KafkaAdminService kafkaAdminService = applicationContext.getBean(KafkaAdminService.class);

        this.adminClient = kafkaAdminService.of();
        this.kafkaConsumerService = applicationContext.getBean(KafkaConsumerService.class);
    }

    public KafkaQueue(Class<T> cls, ApplicationContext applicationContext) {
        this(applicationContext);

        this.cls = cls;
        this.kafkaProducer = applicationContext.getBean(KafkaProducerService.class).of(cls, JsonSerde.of(cls));
        this.topicsConfig = topicsConfig(applicationContext, this.cls);

        KafkaAdminService kafkaAdminService = applicationContext.getBean(KafkaAdminService.class);
        kafkaAdminService.createIfNotExist(this.cls);
    }

    @VisibleForTesting
    public KafkaQueue(String topicKey, Class<T> cls, ApplicationContext applicationContext) {
        this(applicationContext);

        this.cls = cls;
        this.kafkaProducer = applicationContext.getBean(KafkaProducerService.class).of(cls, JsonSerde.of(cls));
        this.topicsConfig = topicsConfig(applicationContext, topicKey);

        KafkaAdminService kafkaAdminService = applicationContext.getBean(KafkaAdminService.class);
        kafkaAdminService.createIfNotExist(topicKey);
    }

    static <T> void log(TopicsConfig topicsConfig, T object, String message) {
        if (log.isTraceEnabled()) {
            log.trace("{} on  topic '{}', value {}", message, topicsConfig.getName(), object);
        } else if (log.isDebugEnabled()) {
            log.trace("{} on topic '{}', key {}", message, topicsConfig.getName(), key(object));
        }
    }

    private void produce(String key, T message) {
        KafkaQueue.log(topicsConfig, message, "Outgoing messsage");

        try {
            kafkaProducer
                .send(
                    new ProducerRecord<>(
                        topicsConfig.getName(),
                        key, message
                    ),
                    (metadata, e) -> {
                        if (e != null) {
                            log.error("Failed to produce '{}' with metadata '{}'", e, metadata);
                        }
                    }
                )
                .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new QueueException("Failed to produce", e);
        }
    }

    @Override
    public void emit(T message) throws QueueException {
        this.produce(key(message), message);
    }

    @Override
    public void delete(T message) throws QueueException {
        this.produce(key(message), null);
    }

    @Override
    public Runnable receive(Consumer<T> consumer) {
        return this.receive(null, consumer);
    }

    @Override
    public Runnable receive(Class<?> consumerGroup, Consumer<T> consumer) {
        AtomicBoolean running = new AtomicBoolean(true);

        poolExecutor.execute(() -> {
            org.apache.kafka.clients.consumer.Consumer<String, T> kafkaConsumer = kafkaConsumerService.of(
                consumerGroup,
                JsonSerde.of(this.cls)
            );

            kafkaConsumers.add(kafkaConsumer);

            if (consumerGroup != null) {
                kafkaConsumer.subscribe(Collections.singleton(topicsConfig.getName()));
            } else {
                kafkaConsumer.assign(this.getTopicPartition());
            }

            while (running.get()) {
                ConsumerRecords<String, T> records = kafkaConsumer.poll(Duration.ofSeconds(1));

                records.forEach(record -> {
                    KafkaQueue.log(topicsConfig, record.value(), "Incoming messsage");

                    consumer.accept(record.value());

                    if (consumerGroup != null) {
                        kafkaConsumer.commitSync(
                            ImmutableMap.of(
                                new TopicPartition(record.topic(), record.partition()),
                                new OffsetAndMetadata(record.offset() + 1)
                            )
                        );
                    }
                });
            }

            kafkaConsumers.remove(kafkaConsumer);
            kafkaConsumer.close();
        });

        return () -> {
            running.set(false);
        };
    }

    static TopicsConfig topicsConfigByTopicName(ApplicationContext applicationContext, String topicName) {
        return applicationContext
            .getBeansOfType(TopicsConfig.class)
            .stream()
            .filter(r -> r.getName().equals(topicName))
            .findFirst()
            .orElseThrow();
    }

    static TopicsConfig topicsConfig(ApplicationContext applicationContext, Class<?> cls) {
        return applicationContext
            .getBeansOfType(TopicsConfig.class)
            .stream()
            .filter(r -> r.getCls() == cls)
            .findFirst()
            .orElseThrow();
    }

    static TopicsConfig topicsConfig(ApplicationContext applicationContext, String name) {
        return applicationContext
            .getBeansOfType(TopicsConfig.class)
            .stream()
            .filter(r -> r.getKey().equals(name))
            .findFirst()
            .orElseThrow();
    }

    private List<TopicPartition> getTopicPartition() {
        try {
            return this.adminClient
                .describeTopics(Collections.singleton(topicsConfig.getName()))
                .all()
                .get()
                .entrySet()
                .stream()
                .flatMap(e -> e.getValue().partitions()
                    .stream()
                    .map(i -> new TopicPartition(e.getValue().name(), i.partition()))
                )
                .collect(Collectors.toList());
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @PreDestroy
    @Override
    public void close() {
        kafkaProducer.close();
        kafkaConsumers.forEach(org.apache.kafka.clients.consumer.Consumer::close);
    }
}
