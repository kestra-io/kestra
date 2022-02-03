package io.kestra.runner.kafka.executors;

import io.kestra.core.runners.ExecutorService;
import io.kestra.core.runners.WorkerInstance;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskRunning;
import io.kestra.runner.kafka.KafkaQueueEnabled;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.kestra.runner.kafka.services.KafkaAdminService;
import io.kestra.runner.kafka.services.KafkaStreamSourceService;
import io.kestra.runner.kafka.services.KafkaStreamsBuilder;
import io.kestra.runner.kafka.streams.GlobalStateProcessor;
import io.kestra.runner.kafka.streams.WorkerInstanceTransformer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;

@KafkaQueueEnabled
@Singleton
@Slf4j
public class ExecutorWorkerRunning implements KafkaExecutorInterface {
    public static final String WORKERINSTANCE_STATE_STORE_NAME = "worker_instance";
    public static final String TOPIC_EXECUTOR_WORKERINSTANCE = "executorworkerinstance";
    public static final String WORKER_RUNNING_STATE_STORE_NAME = "worker_running";

    @Inject
    private KafkaAdminService kafkaAdminService;

    @Inject
    private ExecutorService executorService;

    public StreamsBuilder topology() {
        StreamsBuilder builder = new KafkaStreamsBuilder();

        builder.addGlobalStore(
            Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(WORKERINSTANCE_STATE_STORE_NAME),
                Serdes.String(),
                JsonSerde.of(WorkerInstance.class)
            ),
            kafkaAdminService.getTopicName(KafkaStreamSourceService.TOPIC_EXECUTOR_WORKERINSTANCE),
            Consumed.with(Serdes.String(), JsonSerde.of(WorkerInstance.class)).withName("GlobalStore.ExecutorWorkerInstance"),
            () -> new GlobalStateProcessor<>(WORKERINSTANCE_STATE_STORE_NAME)
        );

        // only used as state store
        builder
            .globalTable(
                kafkaAdminService.getTopicName(WorkerTaskRunning.class),
                Consumed.with(Serdes.String(), JsonSerde.of(WorkerTaskRunning.class)).withName("GlobalKTable.WorkerTaskRunning"),
                Materialized.<String, WorkerTaskRunning, KeyValueStore<Bytes, byte[]>>as(WORKER_RUNNING_STATE_STORE_NAME)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(WorkerTaskRunning.class))
            );

        KStream<String, WorkerInstance> workerInstanceKStream = builder
            .stream(
                kafkaAdminService.getTopicName(WorkerInstance.class),
                Consumed.with(Serdes.String(), JsonSerde.of(WorkerInstance.class)).withName("KStream.WorkerInstance")
            );

        workerInstanceKStream
            .to(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR_WORKERINSTANCE),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerInstance.class)).withName("DetectNewWorker.toExecutorWorkerInstance")
            );

        KStream<String, WorkerInstanceTransformer.Result> stream = workerInstanceKStream
            .transformValues(
                WorkerInstanceTransformer::new,
                Named.as("DetectNewWorker.workerInstanceTransformer")
            )
            .flatMapValues((readOnlyKey, value) -> value, Named.as("DetectNewWorker.flapMapList"));

        // we resend the worker task from evicted worker
        KStream<String, WorkerTask> resultWorkerTask = stream
            .flatMapValues(
                (readOnlyKey, value) -> value.getWorkerTasksToSend(),
                Named.as("DetectNewWorkerTask.flapMapWorkerTaskToSend")
            );

        // and remove from running since already sent
        resultWorkerTask
            .map((key, value) -> KeyValue.pair(value.getTaskRun().getId(), (WorkerTaskRunning)null), Named.as("DetectNewWorkerTask.workerTaskRunningToNull"))
            .to(
                kafkaAdminService.getTopicName(WorkerTaskRunning.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskRunning.class)).withName("DetectNewWorker.toWorkerTaskRunning")
            );

        KafkaStreamSourceService.logIfEnabled(
                log,
                resultWorkerTask,
                (key, value) -> executorService.log(log, false, value),
                "DetectNewWorkerTask"
            )
            .to(
                kafkaAdminService.getTopicName(WorkerTask.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTask.class)).withName("DetectNewWorkerTask.toWorkerTask")
            );

        // we resend the WorkerInstance update
        KStream<String, WorkerInstance> updatedStream = KafkaStreamSourceService.logIfEnabled(
                log,
                stream,
                (key, value) -> log.debug(
                    "Instance updated: {}",
                    value
                ),
                "DetectNewWorkerInstance"
            )
            .map(
                (key, value) -> value.getWorkerInstanceUpdated(),
                Named.as("DetectNewWorkerInstance.mapInstance")
            );

        // cleanup executor workerinstance state store
        updatedStream
            .filter((key, value) -> value != null, Named.as("DetectNewWorkerInstance.filterNotNull"))
            .to(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR_WORKERINSTANCE),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerInstance.class)).withName("DetectNewWorkerInstance.toExecutorWorkerInstance")
            );

        updatedStream
            .to(
                kafkaAdminService.getTopicName(WorkerInstance.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerInstance.class)).withName("DetectNewWorkerInstance.toWorkerInstance")
            );


        return builder;
    }
}
