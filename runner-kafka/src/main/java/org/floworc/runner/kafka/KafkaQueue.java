package org.floworc.runner.kafka;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.runners.WorkerTask;
import org.floworc.core.runners.WorkerTaskResult;
import org.floworc.runner.kafka.configs.TopicsConfig;
import org.floworc.runner.kafka.serializers.JsonSerde;
import org.floworc.runner.kafka.services.KafkaAdminService;
import org.floworc.runner.kafka.services.KafkaConsumerService;
import org.floworc.runner.kafka.services.KafkaProducerService;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
public class KafkaQueue<T> implements QueueInterface<T> {
    private Class<T> cls;
    private KafkaConsumerService kafkaConsumerService;
    private KafkaProducerService kafkaProducerService;
    private TopicsConfig topicsConfig;
    private static ExecutorService poolExecutor = Executors.newCachedThreadPool();

    public KafkaQueue(Class<T> cls, ApplicationContext applicationContext) {
        this.cls = cls;
        this.kafkaConsumerService = applicationContext.getBean(KafkaConsumerService.class);
        this.kafkaProducerService = applicationContext.getBean(KafkaProducerService.class);
        this.topicsConfig = applicationContext
            .getBeansOfType(TopicsConfig.class)
            .stream()
            .filter(r -> r.getCls().equals(this.cls.getName().toLowerCase().replace(".", "-")))
            .findFirst()
            .orElseThrow();

        applicationContext
            .getBean(KafkaAdminService.class)
            .createIfNotExist(this.cls);
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
                .send(new ProducerRecord<>(topicsConfig.getName(), this.key(message), message))
                .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Runnable receive(Class consumerGroup, Consumer<T> consumer) {
        AtomicBoolean running = new AtomicBoolean(true);
        Runnable exitCallback = () -> running.set(false);

        poolExecutor.execute(() -> {
            KafkaConsumer<String, T>  kafkaConsumer = kafkaConsumerService.of(
                consumerGroup,
                JsonSerde.of(this.cls)
            );

            kafkaConsumer.subscribe(Collections.singleton(topicsConfig.getName()));

            while (running.get()) {
                ConsumerRecords<String, T> records = kafkaConsumer.poll(Duration.ofSeconds(1));

                records.forEach(record -> {
                    consumer.accept(record.value());
                    kafkaConsumer.commitSync(
                        ImmutableMap.of(
                            new TopicPartition(record.topic(), record.partition()),
                            new OffsetAndMetadata(record.offset())
                        )
                    );
                });
            }
        });

        return exitCallback;
    }

    public static String getConsumerGroupName(Class group) {
        return "floworc_" +
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
