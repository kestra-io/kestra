package io.kestra.runner.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.queues.QueueService;
import io.kestra.core.runners.*;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowService;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.kestra.runner.kafka.services.KafkaAdminService;
import io.kestra.runner.kafka.services.KafkaStreamService;
import io.kestra.runner.kafka.services.KafkaStreamSourceService;
import io.kestra.runner.kafka.services.KafkaStreamsBuilder;
import io.kestra.runner.kafka.streams.*;
import io.micronaut.context.ApplicationContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.Stores;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@KafkaQueueEnabled
@Singleton
@Slf4j
public class KafkaExecutor extends AbstractExecutor implements Closeable {
    private static final String EXECUTOR_STATE_STORE_NAME = "executor";
    private static final String WORKERTASK_DEDUPLICATION_STATE_STORE_NAME = "workertask_deduplication";
    private static final String TRIGGER_DEDUPLICATION_STATE_STORE_NAME = "trigger_deduplication";
    public static final String TRIGGER_MULTIPLE_STATE_STORE_NAME = "trigger_multiplecondition";
    private static final String NEXTS_DEDUPLICATION_STATE_STORE_NAME = "next_deduplication";
    public static final String WORKER_RUNNING_STATE_STORE_NAME = "worker_running";
    public static final String WORKERINSTANCE_STATE_STORE_NAME = "worker_instance";
    public static final String TOPIC_EXECUTOR_WORKERINSTANCE = "executorworkerinstance";

    protected ApplicationContext applicationContext;
    protected KafkaStreamService kafkaStreamService;
    protected KafkaAdminService kafkaAdminService;
    protected FlowService flowService;
    protected KafkaStreamSourceService kafkaStreamSourceService;
    protected QueueService queueService;

    protected KafkaStreamService.Stream resultStream;

    @Inject
    public KafkaExecutor(
        ApplicationContext applicationContext,
        RunContextFactory runContextFactory,
        KafkaStreamService kafkaStreamService,
        KafkaAdminService kafkaAdminService,
        MetricRegistry metricRegistry,
        FlowService flowService,
        ConditionService conditionService,
        KafkaStreamSourceService kafkaStreamSourceService,
        QueueService queueService
    ) {
        super(runContextFactory, metricRegistry, conditionService);

        this.applicationContext = applicationContext;
        this.kafkaStreamService = kafkaStreamService;
        this.kafkaAdminService = kafkaAdminService;
        this.flowService = flowService;
        this.kafkaStreamSourceService = kafkaStreamSourceService;
        this.queueService = queueService;
    }

    public StreamsBuilder topology() {
        StreamsBuilder builder = new KafkaStreamsBuilder();

        // executor
        builder.addStateStore(Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(EXECUTOR_STATE_STORE_NAME),
            Serdes.String(),
            JsonSerde.of(Executor.class)
        ));

        // WorkerTask deduplication
        builder.addStateStore(Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(WORKERTASK_DEDUPLICATION_STATE_STORE_NAME),
            Serdes.String(),
            Serdes.String()
        ));

        // next deduplication
        builder.addStateStore(Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(NEXTS_DEDUPLICATION_STATE_STORE_NAME),
            Serdes.String(),
            JsonSerde.of(ExecutorProcessTransformer.Store.class)
        ));

        // trigger deduplication
        builder.addStateStore(Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(TRIGGER_DEDUPLICATION_STATE_STORE_NAME),
            Serdes.String(),
            Serdes.String()
        ));

        // trigger multiple flow
        builder.addStateStore(
            Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(TRIGGER_MULTIPLE_STATE_STORE_NAME),
                Serdes.String(),
                JsonSerde.of(MultipleConditionWindow.class)
            )
        );

        // worker instance global state store
        builder.addGlobalStore(
            Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(WORKERINSTANCE_STATE_STORE_NAME),
                Serdes.String(),
                JsonSerde.of(WorkerInstance.class)
            ),
            kafkaAdminService.getTopicName(TOPIC_EXECUTOR_WORKERINSTANCE),
            Consumed.with(Serdes.String(), JsonSerde.of(WorkerInstance.class)).withName("GlobalStore.ExecutorWorkerInstace"),
            () -> new GlobalStateProcessor<>(WORKERINSTANCE_STATE_STORE_NAME)
        );

        // declare ktable & kstream
        KStream<String, WorkerTaskResult> workerTaskResultKStream = this.workerTaskResultKStream(builder);
        KStream<String, Executor> executorKStream = this.executorKStream(builder);
        GlobalKTable<String, Flow> flowKTable = kafkaStreamSourceService.flowGlobalKTable(builder);
        KStream<String, ExecutionKilled> executionKilledKStream = this.executionKilledKStream(builder);
        GlobalKTable<String, WorkerTaskRunning> workerTaskRunningKTable = this.workerTaskRunningKStream(builder);
        KStream<String, WorkerInstance> workerInstanceKStream = this.workerInstanceKStream(builder);
        KStream<String, ExecutorFlowTrigger> flowWithTriggerStream = this.flowWithTriggerStream(builder);
        this.templateKTable(builder);

        // join with killed & worker result
        KStream<String, Executor> executionWithKilled = this.joinExecutionKilled(executionKilledKStream, executorKStream);
        KStream<String, Executor> executionKStream = this.joinWorkerResult(workerTaskResultKStream, executionWithKilled);

        // handle state on execution
        KStream<String, Executor> stream = kafkaStreamSourceService.executorWithFlow(flowKTable, executionKStream);

        stream = this.handleExecutor(stream);

        // save execution
        this.toExecution(stream);
        this.toWorkerTask(stream);
        this.toWorkerTaskResult(stream);

        // trigger
        this.handleExecutorFlowTriggerTopic(stream);
        this.handleFlowTrigger(flowWithTriggerStream);

        // purge at end
        this.purgeExecutor(stream);

        // handle worker
        this.purgeWorkerRunning(workerTaskResultKStream);
        this.detectNewWorker(workerInstanceKStream, workerTaskRunningKTable);

        return builder;
    }

    public KStream<String, Executor> executorKStream(StreamsBuilder builder) {
        KStream<String, Executor> result = builder
            .stream(
                kafkaAdminService.getTopicName(Execution.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("Executor.fromExecution")
            )
            .transformValues(() -> new ValueTransformerWithKey<String, Execution, Executor>() {
                ProcessorContext context;

                @Override
                public void init(ProcessorContext context) {
                    this.context = context;
                }

                @Override
                public Executor transform(String readOnlyKey, Execution value) {
                    if (value == null) {
                        return null;
                    }

                    this.context.headers().remove("from");
                    this.context.headers().remove("offset");

                    return new Executor(
                        value,
                        this.context.offset()
                    );
                }

                @Override
                public void close() {

                }
            }, Named.as("Executor.toExecutor"));

        // logs
        KafkaStreamSourceService.logIfEnabled(
            result,
            (key, value) -> log(log, true, value),
            "ExecutionIn"
        );

        return result;
    }



    private GlobalKTable<String, Template> templateKTable(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(Template.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Template.class)).withName("GlobalKTable.Template"),
                Materialized.<String, Template, KeyValueStore<Bytes, byte[]>>as("template")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Template.class))
            );
    }

    private KStream<String, ExecutionKilled> executionKilledKStream(StreamsBuilder builder) {
        return builder
            .stream(
                kafkaAdminService.getTopicName(ExecutionKilled.class),
                Consumed.with(Serdes.String(), JsonSerde.of(ExecutionKilled.class)).withName("KTable.ExecutionKilled")
            );
    }

    private GlobalKTable<String, WorkerTaskRunning> workerTaskRunningKStream(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(WorkerTaskRunning.class),
                Consumed.with(Serdes.String(), JsonSerde.of(WorkerTaskRunning.class)).withName("GlobalKTable.WorkerTaskRunning"),
                Materialized.<String, WorkerTaskRunning, KeyValueStore<Bytes, byte[]>>as(WORKER_RUNNING_STATE_STORE_NAME)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(WorkerTaskRunning.class))
            );
    }

    private KStream<String, WorkerInstance> workerInstanceKStream(StreamsBuilder builder) {
        return builder
            .stream(
                kafkaAdminService.getTopicName(WorkerInstance.class),
                Consumed.with(Serdes.String(), JsonSerde.of(WorkerInstance.class)).withName("KStream.WorkerInstance")
            );
    }

    private KStream<String, ExecutorFlowTrigger> flowWithTriggerStream(StreamsBuilder builder) {
        return builder
            .stream(
                kafkaAdminService.getTopicName(ExecutorFlowTrigger.class),
                Consumed.with(Serdes.String(), JsonSerde.of(ExecutorFlowTrigger.class)).withName("KStream.ExecutorFlowTrigger")
            )
            .filter((key, value) -> value != null, Named.as("flowwithtriggerstream.filterNotNull"));
    }

    private KStream<String, Executor> joinExecutionKilled(KStream<String, ExecutionKilled> executionKilledKStream, KStream<String, Executor> executorKStream) {
        return executorKStream
            .merge(
                executionKilledKStream
                    .transformValues(
                        () -> new ExecutorKilledJoinerTransformer(
                            EXECUTOR_STATE_STORE_NAME
                        ),
                        Named.as("JoinExecutionKilled.transformValues"),
                        EXECUTOR_STATE_STORE_NAME
                    )
                    .filter((key, value) -> value != null, Named.as("JoinExecutionKilled.filterNotNull")),
                Named.as("JoinExecutionKilled.merge")
            );
    }

    private KStream<String, WorkerTaskResult> workerTaskResultKStream(StreamsBuilder builder) {
        return builder
            .stream(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Consumed.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class)).withName("KStream.WorkerTaskResult")
            )
            .filter((key, value) -> value != null, Named.as("WorkerTaskResultKStream.filterNotNull"));
    }

    private KStream<String, Executor> joinWorkerResult(KStream<String, WorkerTaskResult> workerTaskResultKstream, KStream<String, Executor> executorKStream) {
        return executorKStream
            .merge(
                workerTaskResultKstream
                    .selectKey((key, value) -> value.getTaskRun().getExecutionId(), Named.as("JoinWorkerResult.selectKey"))
                    .mapValues(
                        (key, value) -> new Executor(value),
                        Named.as("JoinWorkerResult.WorkerTaskResultMap")
                    )
                    .repartition(
                        Repartitioned.<String, Executor>as("workertaskjoined")
                            .withKeySerde(Serdes.String())
                            .withValueSerde(JsonSerde.of(Executor.class))
                    ),
                Named.as("JoinWorkerResult.merge")
            )
            .transformValues(
                () -> new ExecutorJoinerTransformer(
                    EXECUTOR_STATE_STORE_NAME,
                    this.metricRegistry
                ),
                Named.as("JoinWorkerResult.transformValues"),
                EXECUTOR_STATE_STORE_NAME
            );
    }

    private KStream<String, Executor> handleExecutor(KStream<String, Executor> stream) {
        return stream
            .transformValues(
                () -> new ExecutorProcessTransformer(
                    NEXTS_DEDUPLICATION_STATE_STORE_NAME,
                    this
                ),
                Named.as("HandleExecutor.transformValues"),
                NEXTS_DEDUPLICATION_STATE_STORE_NAME
            );
    }

    private void purgeExecutor(KStream<String, Executor> stream) {
        KStream<String, Executor> terminatedWithKilled = stream
            .filter(
                (key, value) -> conditionService.isTerminatedWithListeners(value.getFlow(), value.getExecution()),
                Named.as("PurgeExecutor.filterTerminated")
            );

        // we don't purge killed execution in order to have feedback about child running tasks
        // this can be killed lately (after the executor kill the execution), but we want to keep
        // feedback about the actual state (killed or not)
        // @TODO: this can lead to infinite state store for most executor topic
        KStream<String, Executor> terminated = terminatedWithKilled.filter(
            (key, value) -> value.getExecution().getState().getCurrent() != State.Type.KILLED,
            Named.as("PurgeExecutor.filterKilledExecution")
        );

        // clean up executor
        terminated
            .mapValues(
                (readOnlyKey, value) -> (Execution) null,
                Named.as("PurgeExecutor.executionToNull")
            )
            .to(
                kafkaAdminService.getTopicName(KafkaStreamSourceService.TOPIC_EXECUTOR),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("PurgeExecutor.toExecutor")
            );

        // flatMap taskRun
        KStream<String, TaskRun> taskRunKStream = terminated
            .filter(
                (key, value) -> value.getExecution().getTaskRunList() != null,
                Named.as("PurgeExecutor.notNullTaskRunList")
            )
            .flatMapValues(
                (readOnlyKey, value) -> value.getExecution().getTaskRunList(),
                Named.as("PurgeExecutor.flatMapTaskRunList")
            );

        // clean up workerTaskResult
        taskRunKStream
            .map(
                (readOnlyKey, value) -> new KeyValue<>(
                    value.getId(),
                    (WorkerTaskResult) null
                ),
                Named.as("PurgeExecutor.workerTaskResultToNull")
            )
            .to(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class)).withName("PurgeExecutor.toWorkerTaskResult")
            );

        // clean up WorkerTask deduplication state
        taskRunKStream
            .transformValues(
                () -> new DeduplicationPurgeTransformer<>(
                    WORKERTASK_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> value.getExecutionId() + "-" + value.getId()
                ),
                Named.as("PurgeExecutor.purgeWorkerTaskDeduplication"),
                WORKERTASK_DEDUPLICATION_STATE_STORE_NAME
            );

        // clean up Execution Nexts deduplication state
        terminated
            .transformValues(
                () -> new DeduplicationPurgeTransformer<>(
                    NEXTS_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> value.getExecution().getId()
                ),
                Named.as("PurgeExecutor.purgeNextsDeduplication"),
                NEXTS_DEDUPLICATION_STATE_STORE_NAME
            );

        // clean up killed
        terminatedWithKilled
            .mapValues(
                (readOnlyKey, value) -> (ExecutionKilled) null,
                Named.as("PurgeExecutor.executionKilledToNull")
            )
            .to(
                kafkaAdminService.getTopicName(ExecutionKilled.class),
                Produced.with(Serdes.String(), JsonSerde.of(ExecutionKilled.class)).withName("PurgeExecutor.toExecutionKilled")
            );
    }

    private void handleExecutorFlowTriggerTopic(KStream<String, Executor> stream) {
        stream
            .filter(
                (key, value) -> conditionService.isTerminatedWithListeners(value.getFlow(), value.getExecution()),
                Named.as("HandleExecutorFlowTriggerTopic.filterTerminated")
            )
            .transformValues(
                () -> new DeduplicationTransformer<>(
                    "FlowTrigger",
                    TRIGGER_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> value.getExecution().getId(),
                    (key, value) -> value.getExecution().getId()
                ),
                Named.as("HandleExecutorFlowTriggerTopic.deduplication"),
                TRIGGER_DEDUPLICATION_STATE_STORE_NAME
            )
            .filter((key, value) -> value != null, Named.as("HandleExecutorFlowTriggerTopic.deduplicationNotNull"))
            .flatTransform(
                () -> new FlowWithTriggerTransformer(
                    flowService
                ),
                Named.as("HandleExecutorFlowTriggerTopic.flatMapToExecutorFlowTrigger")
            )
            .to(
                kafkaAdminService.getTopicName(ExecutorFlowTrigger.class),
                Produced.with(Serdes.String(), JsonSerde.of(ExecutorFlowTrigger.class)).withName("PurgeExecutor.toExecutorFlowTrigger")
            );
    }

    private void handleFlowTrigger(KStream<String, ExecutorFlowTrigger> stream) {
        stream
            .transformValues(
                () -> new FlowTriggerWithExecutionTransformer(
                    TRIGGER_MULTIPLE_STATE_STORE_NAME,
                    flowService
                ),
                Named.as("HandleFlowTrigger.transformToExecutionList"),
                TRIGGER_MULTIPLE_STATE_STORE_NAME
            )
            .flatMap(
                (key, value) -> value
                    .stream()
                    .map(execution -> new KeyValue<>(execution.getId(), execution))
                    .collect(Collectors.toList()),
                Named.as("HandleFlowTrigger.flapMapToExecution")
            )
            .to(
                kafkaAdminService.getTopicName(Execution.class),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("HandleFlowTrigger.toExecution")
            );

        stream
            .mapValues(
                (readOnlyKey, value) -> (ExecutorFlowTrigger)null,
                Named.as("HandleFlowTrigger.executorFlowTriggerToNull")
            )
            .to(
                kafkaAdminService.getTopicName(ExecutorFlowTrigger.class),
                Produced.with(Serdes.String(), JsonSerde.of(ExecutorFlowTrigger.class)).withName("HandleFlowTrigger.toExecutorFlowTrigger")
            );
    }

    private void toWorkerTask(KStream<String, Executor> stream) {
        // deduplication worker task
        KStream<String, WorkerTask> dedupWorkerTask = stream
            .flatMapValues(
                (readOnlyKey, value) -> value.getWorkerTasks(),
                Named.as("HandleWorkerTask.flatMapToWorkerTask")
            )
            .transformValues(
                () -> new DeduplicationTransformer<>(
                    "WorkerTask",
                    WORKERTASK_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> value.getTaskRun().getExecutionId() + "-" + value.getTaskRun().getId(),
                    (key, value) -> value.getTaskRun().getState().getCurrent().name()
                ),
                Named.as("HandleWorkerTask.deduplication"),
                WORKERTASK_DEDUPLICATION_STATE_STORE_NAME
            )
            .filter((key, value) -> value != null, Named.as("HandleWorkerTask.notNullFilter"));

        // flowable > running to WorkerTaskResult
        KStream<String, WorkerTaskResult> resultFlowable = dedupWorkerTask
            .filter((key, value) -> value.getTask().isFlowable(), Named.as("HandleWorkerTaskFlowable.filterIsFlowable"))
            .mapValues(
                (key, value) -> new WorkerTaskResult(value.withTaskRun(value.getTaskRun().withState(State.Type.RUNNING))),
                Named.as("HandleWorkerTaskFlowable.toRunning")
            )
            .map(
                (key, value) -> new KeyValue<>(queueService.key(value), value),
                Named.as("HandleWorkerTaskFlowable.mapWithKey")
            )
            .selectKey(
                (key, value) -> queueService.key(value),
                Named.as("HandleWorkerTaskFlowable.selectKey")
            );

        KStream<String, WorkerTaskResult> workerTaskResultKStream = KafkaStreamSourceService.logIfEnabled(
            resultFlowable,
            (key, value) -> log(log, false, value),
            "HandleWorkerTaskFlowable"
        );

        workerTaskResultKStream
            .to(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class)).withName("HandleWorkerTaskFlowable.toWorkerTaskResult")
            );

        // not flowable > to WorkerTask
        KStream<String, WorkerTask> resultNotFlowable = dedupWorkerTask
            .filter((key, value) -> !value.getTask().isFlowable(), Named.as("HandleWorkerTaskNotFlowable.filterIsNotFlowable"))
            .map((key, value) -> new KeyValue<>(queueService.key(value), value), Named.as("HandleWorkerTaskNotFlowable.mapWithKey"))
            .selectKey(
                (key, value) -> queueService.key(value),
                Named.as("HandleWorkerTaskNotFlowable.selectKey")
            );

        KStream<String, WorkerTask> workerTaskKStream = KafkaStreamSourceService.logIfEnabled(
            resultNotFlowable,
            (key, value) -> log(log, false, value),
            "HandleWorkerTaskNotFlowable"
        );

        workerTaskKStream
            .to(
                kafkaAdminService.getTopicName(WorkerTask.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTask.class)).withName("HandleWorkerTaskNotFlowable.toWorkerTask")
            );
    }

    private void toWorkerTaskResult(KStream<String, Executor> stream) {
        KStream<String, WorkerTaskResult> workerTaskResultKStream = stream
            .flatMapValues(
                (readOnlyKey, value) -> value.getWorkerTaskResults(),
                Named.as("HandleWorkerTaskResult.flapMap")
            )
            .transformValues(
                () -> new DeduplicationTransformer<>(
                    "WorkerTaskResult",
                    WORKERTASK_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> value.getTaskRun().getExecutionId() + "-" + value.getTaskRun().getId(),
                    (key, value) -> value.getTaskRun().getState().getCurrent().name()
                ),
                Named.as("HandleWorkerTaskResult.deduplication"),
                WORKERTASK_DEDUPLICATION_STATE_STORE_NAME
            )
            .filter((key, value) -> value != null, Named.as("HandleWorkerTaskResult.notNullFilter"))
            .selectKey(
                (key, value) -> value.getTaskRun().getId(),
                Named.as("HandleWorkerTaskResult.selectKey")
            );

        KafkaStreamSourceService.logIfEnabled(
            workerTaskResultKStream,
            (key, value) -> log(log, false, value),
            "HandleWorkerTaskResult"
        )
            .to(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class)).withName("HandleWorkerTaskResult.toWorkerTaskResult")
            );
    }

    private void purgeWorkerRunning(KStream<String, WorkerTaskResult> workerTaskResultKStream) {
        workerTaskResultKStream
            .filter((key, value) -> value.getTaskRun().getState().isTerninated(), Named.as("PurgeWorkerRunning.filterTerminated"))
            .mapValues((readOnlyKey, value) -> (WorkerTaskRunning)null, Named.as("PurgeWorkerRunning.toNull"))
            .to(
                kafkaAdminService.getTopicName(WorkerTaskRunning.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskRunning.class)).withName("PurgeWorkerRunning.toWorkerTaskRunning")
            );
    }

    private void detectNewWorker(KStream<String, WorkerInstance> workerInstanceKStream, GlobalKTable<String, WorkerTaskRunning> workerTaskRunningGlobalKTable) {
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
            resultWorkerTask,
            (key, value) -> log.debug(
                ">> OUT WorkerTask resend : {}",
                value.getTaskRun().toStringState()
            ),
            "DetectNewWorkerTask"
        )
            .to(
                kafkaAdminService.getTopicName(WorkerTask.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTask.class)).withName("DetectNewWorkerTask.toWorkerTask")
            );

        // we resend the WorkerInstance update
        KStream<String, WorkerInstance> updatedStream = KafkaStreamSourceService.logIfEnabled(
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
    }

    private void toExecution(KStream<String, Executor> stream) {
        KStream<String, Executor> streamFrom = stream
            .filter((key, value) -> value.isExecutionUpdated(), Named.as("ToExecution.haveFrom"))
            .transformValues(
                () -> new ValueTransformer<Executor, Executor>() {
                    ProcessorContext context;

                    @Override
                    public void init(ProcessorContext context) {
                        this.context = context;
                    }

                    @Override
                    public Executor transform(Executor value) {
                        try {
                            this.context.headers().add(
                                "from",
                                JacksonMapper.ofJson().writeValueAsString(value.getFrom()).getBytes(StandardCharsets.UTF_8)
                            );

                            this.context.headers().add(
                                "offset",
                                JacksonMapper.ofJson().writeValueAsString(value.getOffset()).getBytes(StandardCharsets.UTF_8)
                            );
                        } catch (JsonProcessingException e) {
                            log.warn("Unable to add headers", e);
                        }

                        return value;
                    }

                    @Override
                    public void close() {

                    }
                },
                Named.as("ToExecution.addHeaders")
            );

        // send execution
        KStream<String, Executor> executionKStream = streamFrom
            .filter((key, value) -> value.getException() == null, Named.as("ToExecutionExecution.notException"));

        toExecutionSend(executionKStream, "ToExecutionExecution");

        // send exception
        KStream<String, Pair<Executor, Execution.FailedExecutionWithLog>> failedStream = streamFrom
            .filter((key, value) -> value.getException() != null, Named.as("ToExecutionException.isException"))
            .mapValues(
                e -> Pair.of(e, e.getExecution().failedExecutionFromExecutor(e.getException())),
                Named.as("ToExecutionException.mapToFailedExecutionWithLog")
            );

        failedStream
            .flatMapValues(e -> e.getRight().getLogs(), Named.as("ToExecutionException.flatmapLogs"))
            .to(
                kafkaAdminService.getTopicName(LogEntry.class),
                Produced.with(Serdes.String(), JsonSerde.of(LogEntry.class)).withName("ToExecutionException.toLogEntry")
            );

        KStream<String, Executor> executorFailedKStream = failedStream
            .mapValues(e -> e.getLeft().withExecution(e.getRight().getExecution(), "failedExecutionFromExecutor"), Named.as("ToExecutionException.mapToExecutor"));

        toExecutionSend(executorFailedKStream, "ToExecutionException");
    }

    private void toExecutionSend(KStream<String, Executor> stream, String from) {
        stream = KafkaStreamSourceService.logIfEnabled(
            stream,
            (key, value) -> log(log, false, value),
            from
        );

        stream
            .transformValues(
                () -> new StateStoreTransformer<>(EXECUTOR_STATE_STORE_NAME, Executor::serialize),
                Named.as(from + ".store"),
                EXECUTOR_STATE_STORE_NAME
            )
            .mapValues((readOnlyKey, value) -> value.getExecution(), Named.as(from + ".mapToExecution"))
            .to(
                kafkaAdminService.getTopicName(Execution.class),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class)).withName(from + ".toExecution")
            );
    }

    @NoArgsConstructor
    @Getter
    public static class WorkerTaskResultState {
        Map<String, WorkerTaskResult> results = new HashMap<>();
    }

    @AllArgsConstructor
    @Getter
    public static class WorkerTaskRunningWithWorkerTaskRunning {
        WorkerInstance workerInstance;
        WorkerTaskRunning workerTaskRunning;
    }

    @NoArgsConstructor
    @Getter
    public static class WorkerTaskRunningState {
        Map<String, WorkerTaskRunning> workerTaskRunnings = new HashMap<>();
    }

    @Override
    public void run() {
        kafkaAdminService.createIfNotExist(WorkerTask.class);
        kafkaAdminService.createIfNotExist(WorkerTaskResult.class);
        kafkaAdminService.createIfNotExist(Execution.class);
        kafkaAdminService.createIfNotExist(Flow.class);
        kafkaAdminService.createIfNotExist(KafkaStreamSourceService.TOPIC_EXECUTOR);
        kafkaAdminService.createIfNotExist(KafkaStreamSourceService.TOPIC_EXECUTOR_WORKERINSTANCE);
        kafkaAdminService.createIfNotExist(ExecutionKilled.class);
        kafkaAdminService.createIfNotExist(WorkerTaskRunning.class);
        kafkaAdminService.createIfNotExist(WorkerInstance.class);
        kafkaAdminService.createIfNotExist(Template.class);
        kafkaAdminService.createIfNotExist(LogEntry.class);
        kafkaAdminService.createIfNotExist(Trigger.class);
        kafkaAdminService.createIfNotExist(ExecutorFlowTrigger.class);

        Properties properties = new Properties();

        // hack, we send application context in order to use on exception handler
        properties.put(StreamsConfig.DEFAULT_PRODUCTION_EXCEPTION_HANDLER_CLASS_CONFIG, KafkaExecutorProductionExceptionHandler.class);
        properties.put(KafkaExecutorProductionExceptionHandler.APPLICATION_CONTEXT_CONFIG, applicationContext);

        // build
        Topology topology = this.topology().build();

        if (log.isTraceEnabled()) {
            log.trace(topology.describe().toString());
        }

        resultStream = kafkaStreamService.of(this.getClass(), topology, properties);
        resultStream.start();

        applicationContext.registerSingleton(new KafkaTemplateExecutor(
            resultStream.store(StoreQueryParameters.fromNameAndType("template", QueryableStoreTypes.keyValueStore()))
        ));
    }

    @Override
    public void close() throws IOException {
        if (this.resultStream != null) {
            this.resultStream.close(Duration.ofSeconds(10));
            this.resultStream = null;
        }
    }

}
