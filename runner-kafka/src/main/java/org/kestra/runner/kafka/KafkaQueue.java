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
import org.kestra.core.models.executions.ExecutionKilled;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.templates.Template;
import org.kestra.core.queues.QueueException;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.runners.WorkerInstance;
import org.kestra.core.runners.WorkerTask;
import org.kestra.core.runners.WorkerTaskResult;
import org.kestra.core.runners.WorkerTaskRunning;
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
            ExecutorsUtils executorsUtils = applicationContext.getBean(ExecutorsUtils.class);
            poolExecutor = executorsUtils.cachedThreadPool("kakfa-queue");
        }

        KafkaAdminService kafkaAdminService = applicationContext.getBean(KafkaAdminService.class);

        this.cls = cls;
        this.adminClient = kafkaAdminService.of();
        this.kafkaConsumerService = applicationContext.getBean(KafkaConsumerService.class);
        this.kafkaProducer = applicationContext.getBean(KafkaProducerService.class).of(cls, JsonSerde.of(cls));
        this.topicsConfig = topicsConfig(applicationContext, this.cls);

        kafkaAdminService.createIfNotExist(this.cls);
    }

    static String key(Object object) {
        if (object.getClass() == Execution.class) {
            return ((Execution) object).getId();
        } else if (object.getClass() == WorkerTask.class) {
            return ((WorkerTask) object).getTaskRun().getId();
        } else if (object.getClass() == WorkerTaskRunning.class) {
            return ((WorkerTaskRunning) object).getTaskRun().getId();
        } else if (object.getClass() == WorkerInstance.class) {
            return ((WorkerInstance) object).getWorkerUuid().toString();
        } else if (object.getClass() == WorkerTaskResult.class) {
            return ((WorkerTaskResult) object).getTaskRun().getId();
        } else if (object.getClass() == LogEntry.class) {
            return null;
        } else if (object.getClass() == Flow.class) {
            return ((Flow) object).uid();
        } else if (object.getClass() == Template.class) {
            return ((Template) object).uid();
        } else if (object.getClass() == ExecutionKilled.class) {
            return ((ExecutionKilled) object).getExecutionId();
        } else {
            throw new IllegalArgumentException("Unknown type '" + object.getClass().getName() + "'");
        }
    }

    static <T> void log(TopicsConfig topicsConfig, T object, String message) {
        if (log.isTraceEnabled()) {
            log.trace("{} on  topic '{}', value {}", message, topicsConfig.getName(), object);
        } else if (log.isDebugEnabled()) {
            log.trace("{} on topic '{}', key {}", message, topicsConfig.getName(), key(object));
        }
    }

    @Override
    public void emit(T message) throws QueueException {
        KafkaQueue.log(topicsConfig, message, "Outgoing messsage");

        try {
            kafkaProducer
                .send(new ProducerRecord<>(
                    topicsConfig.getName(),
                    key(message), message),
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

    static TopicsConfig topicsConfig(ApplicationContext applicationContext, Class<?> cls) {
        return applicationContext
            .getBeansOfType(TopicsConfig.class)
            .stream()
            .filter(r -> r.getCls() == cls)
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
