package org.kestra.runner.kafka;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.queues.QueueException;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.runners.WorkerTask;
import org.kestra.core.runners.WorkerTaskResult;
import org.kestra.core.utils.ThreadMainFactoryBuilder;
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
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;

@Slf4j
public class KafkaQueue<T> implements QueueInterface<T>, AutoCloseable {
    private final Class<T> cls;
    private final AdminClient adminClient;
    private final KafkaConsumerService kafkaConsumerService;
    private final KafkaProducer<String, T> kafkaProducer;
    private final List<KafkaConsumer<String, T>> kafkaConsumers = new ArrayList<>();
    private final TopicsConfig topicsConfig;
    private static ExecutorService poolExecutor;

    public KafkaQueue(Class<T> cls, ApplicationContext applicationContext) {
        if (poolExecutor == null) {
            poolExecutor = Executors.newCachedThreadPool(
                applicationContext.getBean(ThreadMainFactoryBuilder.class).build("kakfa-queue-%d")
            );
        }

        KafkaAdminService kafkaAdminService = applicationContext.getBean(KafkaAdminService.class);

        this.cls = cls;
        this.adminClient = kafkaAdminService.of();
        this.kafkaConsumerService = applicationContext.getBean(KafkaConsumerService.class);
        this.kafkaProducer = applicationContext.getBean(KafkaProducerService.class).of(cls, JsonSerde.of(cls));
        this.topicsConfig = applicationContext
            .getBeansOfType(TopicsConfig.class)
            .stream()
            .filter(r -> r.getCls() == this.cls)
            .findFirst()
            .orElseThrow();

        kafkaAdminService.createIfNotExist(this.cls);
    }

    private String key(Object object) {
        if (this.cls == Execution.class) {
            return ((Execution) object).getId();
        } else if (this.cls == WorkerTask.class) {
            return ((WorkerTask) object).getTaskRun().getId();
        } else if (this.cls == WorkerTaskResult.class) {
            return ((WorkerTaskResult) object).getTaskRun().getId();
        } else {
            throw new IllegalArgumentException("Unknown type '" + this.cls.getName() + "'");
        }
    }

    @Override
    public void emit(T message) throws QueueException {
        if (log.isTraceEnabled()) {
            log.trace("New message: topic '{}', value {}", topicsConfig.getName(), message);
        }

        try {
            kafkaProducer
                .send(new ProducerRecord<>(
                    topicsConfig.getName(),
                    this.key(message), message),
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
    public Runnable receive(Consumer<T> consumer) {
        return this.receive(null, consumer);
    }

    @Override
    public Runnable receive(Class<?> consumerGroup, Consumer<T> consumer) {
        AtomicBoolean running = new AtomicBoolean(true);

        poolExecutor.execute(() -> {
            KafkaConsumer<String, T> kafkaConsumer = kafkaConsumerService.of(
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
        });

        return () -> {
            running.set(false);
        };
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

    public static String getConsumerGroupName(Class<?> group) {
        return "kestra_" +
            CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE,
                group.getSimpleName().replace("Kafka", "")
            );
    }

    @PreDestroy
    @Override
    public void close() {
        kafkaProducer.close();
        kafkaConsumers.forEach(KafkaConsumer::close);
    }
}
