package io.kestra.runner.kafka;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.queues.QueueService;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.RetryUtils;
import io.kestra.runner.kafka.configs.TopicsConfig;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.kestra.runner.kafka.services.KafkaAdminService;
import io.kestra.runner.kafka.services.KafkaConsumerService;
import io.kestra.runner.kafka.services.KafkaProducerService;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.WakeupException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;

@Slf4j
public class KafkaQueue<T> implements QueueInterface<T>, AutoCloseable {
    private Class<T> cls;
    private final AdminClient adminClient;
    private final KafkaConsumerService kafkaConsumerService;
    private final List<org.apache.kafka.clients.consumer.Consumer<String, T>> kafkaConsumers = Collections.synchronizedList(new ArrayList<>());
    private final QueueService queueService;
    private final KafkaQueueService kafkaQueueService;
    private final RetryUtils retryUtils;

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
        this.queueService = applicationContext.getBean(QueueService.class);
        this.kafkaQueueService = applicationContext.getBean(KafkaQueueService.class);
        this.retryUtils = applicationContext.getBean(RetryUtils.class);
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

    private void produce(String key, T message) {
        this.kafkaQueueService.log(log, topicsConfig, key, message, "Outgoing messsage");

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
        this.produce(this.queueService.key(message), message);
    }

    @Override
    public void delete(T message) throws QueueException {
        this.produce(this.queueService.key(message), null);
    }

    @Override
    public Runnable receive(Consumer<T> consumer) {
        return this.receive(null, consumer);
    }

    @Override
    public Runnable receive(Class<?> consumerGroup, Consumer<T> consumer) {
        AtomicBoolean running = new AtomicBoolean(true);

        // no consumer groups, we fetch actual offset and block until the response id ready
        // we need to be sure to get from actual time, so consume last 1 sec must enough
        Map<TopicPartition, Long> offsets = null;
        if (consumerGroup == null) {
            offsets = this.offsetForTime(Instant.now().minus(Duration.ofSeconds(1)));
        }
        Map<TopicPartition, Long> finalOffsets = offsets;

        poolExecutor.execute(() -> {
            org.apache.kafka.clients.consumer.Consumer<String, T> kafkaConsumer = kafkaConsumerService.of(
                consumerGroup,
                JsonSerde.of(this.cls)
            );

            kafkaConsumers.add(kafkaConsumer);

            if (consumerGroup != null) {
                kafkaConsumer.subscribe(Collections.singleton(topicsConfig.getName()));
            } else {
                kafkaConsumer.assign(new ArrayList<>(finalOffsets.keySet()));
                finalOffsets.forEach(kafkaConsumer::seek);
            }

            while (running.get()) {
                try {
                    ConsumerRecords<String, T> records = kafkaConsumer.poll(Duration.ofMillis(500));

                    records.forEach(record -> {
                        this.kafkaQueueService.log(log, topicsConfig, record.key(), record.value(), "Incoming messsage");

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
                } catch (WakeupException e) {
                    log.debug("Received Wakeup on {} with type {}!", this.getClass().getName(), this.cls.getName());

                    // first call, we want to shutdown, so pause the consumer, will be closed after properly on second call
                    if (kafkaConsumer.paused().size() == 0) {
                        kafkaConsumer.pause(kafkaConsumer.assignment());
                    } else {
                        running.set(false);
                    }
                }
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
            .orElseThrow(() -> new IllegalArgumentException("Invalid topic name '" + topicName + "'"));
    }

    static TopicsConfig topicsConfig(ApplicationContext applicationContext, Class<?> cls) {
        return applicationContext
            .getBeansOfType(TopicsConfig.class)
            .stream()
            .filter(r -> r.getCls() == cls)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Invalid topic class '" + cls.getName() + "'"));
    }

    static TopicsConfig topicsConfig(ApplicationContext applicationContext, String name) {
        return applicationContext
            .getBeansOfType(TopicsConfig.class)
            .stream()
            .filter(r -> r.getKey().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Invalid topic key '" + name + "'"));
    }

    private List<TopicPartition> listTopicPartition() throws ExecutionException, InterruptedException {
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
    }

    Map<TopicPartition, Long> offsetForTime(Instant instant) {
        org.apache.kafka.clients.consumer.Consumer<String, T> consumer = kafkaConsumerService.of(
            null,
            JsonSerde.of(this.cls)
        );

        try {
            List<TopicPartition> topicPartitions = this.listTopicPartition();
            Map<TopicPartition, Long> result = retryUtils.<Map<TopicPartition, Long>, TimeoutException>of().run(
                TimeoutException.class,
                () -> consumer.endOffsets(topicPartitions)
            );

            result.putAll(consumer
                .offsetsForTimes(
                    topicPartitions
                        .stream()
                        .map(e -> new AbstractMap.SimpleEntry<>(
                            e,
                            instant.toEpochMilli()
                        ))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                )
                .entrySet()
                .stream()
                .filter(e -> e.getValue() != null)
                .map(e -> new AbstractMap.SimpleEntry<>(
                    e.getKey(),
                    e.getValue().offset()
                ))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
            );

            return result;
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void pause() {
        this.wakeup();
    }

    @PreDestroy
    @Override
    public void close() {
        kafkaProducer.close();
        this.wakeup();
    }

    private void wakeup() {
        kafkaConsumers.forEach(org.apache.kafka.clients.consumer.Consumer::wakeup);
    }
}
