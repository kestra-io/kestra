package org.kestra.runner.kafka;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.runners.WorkerTask;
import org.kestra.core.runners.WorkerTaskResult;
import org.kestra.core.utils.UncaughtExceptionHandlers;
import org.kestra.runner.kafka.configs.TopicsConfig;
import org.kestra.runner.kafka.serializers.JsonSerde;
import org.kestra.runner.kafka.services.KafkaAdminService;
import org.kestra.runner.kafka.services.KafkaConsumerService;
import org.kestra.runner.kafka.services.KafkaProducerService;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class KafkaQueue<T> implements QueueInterface<T> {
    private Class<T> cls;
    private KafkaAdminService kafkaAdminService;
    private KafkaConsumerService kafkaConsumerService;
    private KafkaProducerService kafkaProducerService;
    private TopicsConfig topicsConfig;
    private static ExecutorService poolExecutor = Executors.newCachedThreadPool(
        new ThreadFactoryBuilder()
            .setNameFormat("kakfa-queue-%d")
            .setUncaughtExceptionHandler(UncaughtExceptionHandlers.systemExit())
            .build()
    );

    public KafkaQueue(Class<T> cls, ApplicationContext applicationContext) {
        this.cls = cls;
        this.kafkaAdminService = applicationContext.getBean(KafkaAdminService.class);
        this.kafkaConsumerService = applicationContext.getBean(KafkaConsumerService.class);
        this.kafkaProducerService = applicationContext.getBean(KafkaProducerService.class);
        this.topicsConfig = applicationContext
            .getBeansOfType(TopicsConfig.class)
            .stream()
            .filter(r -> r.getCls().equals(this.cls.getName().toLowerCase().replace(".", "-")))
            .findFirst()
            .orElseThrow();

        this.kafkaAdminService.createIfNotExist(this.cls);
    }

    private String key(Object object) {
        if (this.cls == Execution.class) {
            return ((Execution) object).getId();
        } else if (this.cls == WorkerTask.class) {
            return ((WorkerTask) object).getTaskRun().getExecutionId();
        } else if (this.cls == WorkerTaskResult.class) {
            return ((WorkerTaskResult) object).getTaskRun().getExecutionId();
        } else {
            throw new IllegalArgumentException("Unknown type '" + this.cls.getName() + "'");
        }
    }

    @Override
    public void emit(T message) {
        if (log.isTraceEnabled()) {
            log.trace("New message: topic '{}', value {}", topicsConfig.getName(), message);
        }

        try {
            kafkaProducerService
                .of(cls, JsonSerde.of(cls))
                .send(new ProducerRecord<>(
                    topicsConfig.getName(),
                    this.key(message), message),
                    (metadata, e) -> {
                        if (e != null) {
                            log.error("Failed to produce with metadata {}", metadata.toString());
                        }
                    }
                )
                .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Runnable receive(Consumer<T> consumer) {
        return this.receive(null, consumer);
    }

    @Override
    public Runnable receive(Class<?> consumerGroup, Consumer<T> consumer) {
        AtomicBoolean running = new AtomicBoolean(true);

        Future<?> submit = poolExecutor.submit(() -> {
            KafkaConsumer<String, T> kafkaConsumer = kafkaConsumerService.of(
                consumerGroup,
                JsonSerde.of(this.cls)
            );
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
            return this.kafkaAdminService.of()
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

    public static String getConsumerGroupName(Class group) {
        return "kestra_" +
            CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE,
                group.getSimpleName().replace("Kafka", "")
            );
    }

    @Override
    public void close() throws IOException {
        if (!poolExecutor.isShutdown()) {
            poolExecutor.shutdown();
        }
    }
}
