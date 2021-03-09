package org.kestra.runner.kafka;

import com.google.common.collect.ImmutableMap;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.queues.QueueService;
import org.kestra.core.queues.WorkerTaskQueueInterface;
import org.kestra.core.runners.WorkerInstance;
import org.kestra.core.runners.WorkerTask;
import org.kestra.core.runners.WorkerTaskRunning;
import org.kestra.core.utils.Await;
import org.kestra.core.utils.ExecutorsUtils;
import org.kestra.runner.kafka.configs.TopicsConfig;
import org.kestra.runner.kafka.serializers.JsonSerde;
import org.kestra.runner.kafka.services.KafkaAdminService;
import org.kestra.runner.kafka.services.KafkaConfigService;
import org.kestra.runner.kafka.services.KafkaConsumerService;
import org.kestra.runner.kafka.services.KafkaProducerService;

import java.net.InetAddress;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class KafkaWorkerTaskQueue implements WorkerTaskQueueInterface {
    private final QueueInterface<WorkerInstance> workerInstanceQueue;
    private final TopicsConfig topicsConfigWorkerTask;
    private final TopicsConfig topicsConfigWorkerTaskRunning;
    private final KafkaProducer<String, WorkerTaskRunning> kafkaProducer;
    private final KafkaConsumerService kafkaConsumerService;
    private final KafkaConfigService kafkaConfigService;
    private final QueueService queueService;
    private final KafkaQueueService kafkaQueueService;
    private final AtomicReference<WorkerInstance> workerInstance = new AtomicReference<>();
    private final UUID workerUuid;

    private static ExecutorService poolExecutor;
    private final List<org.apache.kafka.clients.consumer.Consumer<String, WorkerTask>> kafkaConsumers = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public KafkaWorkerTaskQueue(ApplicationContext applicationContext) {
        if (poolExecutor == null) {
            ExecutorsUtils executorsUtils = applicationContext.getBean(ExecutorsUtils.class);
            poolExecutor = executorsUtils.cachedThreadPool("kakfa-workertask-queue");
        }

        this.workerUuid = UUID.randomUUID();
        this.kafkaProducer = applicationContext.getBean(KafkaProducerService.class).of(
            WorkerTaskRunning.class,
            JsonSerde.of(WorkerTaskRunning.class),
            ImmutableMap.of("transactional.id", this.workerUuid.toString())
        );
        this.topicsConfigWorkerTask = KafkaQueue.topicsConfig(applicationContext, WorkerTask.class);
        this.topicsConfigWorkerTaskRunning = KafkaQueue.topicsConfig(applicationContext, WorkerTaskRunning.class);
        this.kafkaConsumerService = applicationContext.getBean(KafkaConsumerService.class);
        this.kafkaConfigService = applicationContext.getBean(KafkaConfigService.class);
        this.queueService = applicationContext.getBean(QueueService.class);
        this.kafkaQueueService = applicationContext.getBean(KafkaQueueService.class);
        this.workerInstanceQueue = (QueueInterface<WorkerInstance>) applicationContext.getBean(
            QueueInterface.class,
            Qualifiers.byName(QueueFactoryInterface.WORKERINSTANCE_NAMED)
        );
        KafkaAdminService kafkaAdminService = applicationContext.getBean(KafkaAdminService.class);
        kafkaAdminService.createIfNotExist(WorkerTask.class);
    }

    public Runnable receive(Class<?> consumerGroup, Consumer<WorkerTask> consumer) {
        AtomicBoolean running = new AtomicBoolean(true);

        poolExecutor.execute(() -> {
            kafkaProducer.initTransactions();

            org.apache.kafka.clients.consumer.Consumer<String, WorkerTask> kafkaConsumer = kafkaConsumerService.of(
                consumerGroup,
                JsonSerde.of(WorkerTask.class),
                ImmutableMap.of("client.id", this.workerUuid.toString()),
                consumerRebalanceListener()
            );

            kafkaConsumers.add(kafkaConsumer);

            kafkaConsumer.subscribe(Collections.singleton(topicsConfigWorkerTask.getName()));

            while (running.get()) {
                try {
                    ConsumerRecords<String, WorkerTask> records = kafkaConsumer.poll(Duration.ofMillis(100));

                    if (!records.isEmpty()) {
                        kafkaProducer.beginTransaction();

                        records.forEach(record -> {
                            this.kafkaQueueService.log(
                                log,
                                this.topicsConfigWorkerTask,
                                record.value(),
                                "Incoming messsage"
                            );

                            if (workerInstance.get() == null) {
                                Await.until(() -> workerInstance.get() != null);
                            }

                            WorkerTaskRunning workerTaskRunning = WorkerTaskRunning.of(
                                record.value(),
                                workerInstance.get(),
                                record.partition()
                            );

                            this.kafkaProducer.send(new ProducerRecord<>(
                                topicsConfigWorkerTaskRunning.getName(),
                                this.queueService.key(workerTaskRunning),
                                workerTaskRunning
                            ));

                            this.kafkaQueueService.log(
                                log,
                                this.topicsConfigWorkerTaskRunning,
                                workerTaskRunning,
                                "Outgoing messsage"
                            );
                        });

                        // we commit first all offset before submit task to worker
                        kafkaProducer.sendOffsetsToTransaction(
                            KafkaConsumerService.maxOffsets(records),
                            kafkaConfigService.getConsumerGroupName(consumerGroup)
                        );
                        kafkaProducer.commitTransaction();

                        // now, we can submit to worker to be sure we don't have a WorkerTaskResult before commiting the offset!
                        records.forEach(record -> {
                            consumer.accept(record.value());
                        });
                    }
                } catch (WakeupException e) {
                    log.debug("Received Wakeup on {}!", this.getClass().getName());
                    running.set(false);
                }
            }

            kafkaConsumers.remove(kafkaConsumer);
            kafkaConsumer.close();
        });

        return () -> running.set(false);
    }

    private ConsumerRebalanceListener consumerRebalanceListener() {
        return new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                this.send(partitions);
            }

            @SneakyThrows
            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                this.send(partitions);
            }

            @SneakyThrows
            private void send(Collection<TopicPartition> partitions) {
                workerInstance.set(WorkerInstance
                    .builder()
                    .partitions(partitions
                        .stream()
                        .map(TopicPartition::partition)
                        .collect(Collectors.toList())
                    )
                    .workerUuid(workerUuid)
                    .hostname(InetAddress.getLocalHost().getHostName())
                    .build()
                );

                workerInstanceQueue.emit(workerInstance.get());
            }
        };
    }

    @PreDestroy
    public void close() {
        kafkaProducer.close();
        kafkaConsumers.forEach(org.apache.kafka.clients.consumer.Consumer::wakeup);
    }
}
