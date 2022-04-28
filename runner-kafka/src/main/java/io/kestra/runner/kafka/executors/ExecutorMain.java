package io.kestra.runner.kafka.executors;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueService;
import io.kestra.core.runners.*;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.FlowService;
import io.kestra.runner.kafka.KafkaFlowExecutor;
import io.kestra.runner.kafka.KafkaQueueEnabled;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.kestra.runner.kafka.services.KafkaAdminService;
import io.kestra.runner.kafka.services.KafkaStreamSourceService;
import io.kestra.runner.kafka.services.KafkaStreamsBuilder;
import io.kestra.runner.kafka.streams.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.event.Level;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@KafkaQueueEnabled
@Singleton
@Slf4j
public class ExecutorMain implements KafkaExecutorInterface {
    private static final String EXECUTOR_STATE_STORE_NAME = "executor";
    private static final String WORKERTASK_DEDUPLICATION_STATE_STORE_NAME = "workertask_deduplication";
    private static final String TRIGGER_DEDUPLICATION_STATE_STORE_NAME = "trigger_deduplication";
    private static final String NEXTS_DEDUPLICATION_STATE_STORE_NAME = "next_deduplication";
    private static final String EXECUTION_DELAY_STATE_STORE_NAME = "execution_delay";

    @Inject
    private KafkaAdminService kafkaAdminService;

    @Inject
    private FlowService flowService;

    @Inject
    private KafkaStreamSourceService kafkaStreamSourceService;

    @Inject
    private QueueService queueService;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    private ConditionService conditionService;

    @Inject
    private ExecutorService executorService;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private KafkaFlowExecutor kafkaFlowExecutor;

    @Inject
    private ExecutionService executionService;

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
            JsonSerde.of(ExecutorNextTransformer.Store.class)
        ));

        // trigger deduplication
        builder.addStateStore(Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(TRIGGER_DEDUPLICATION_STATE_STORE_NAME),
            Serdes.String(),
            Serdes.String()
        ));

        // Execution delay
        builder.addStateStore(Stores.windowStoreBuilder(
            Stores.persistentWindowStore(EXECUTION_DELAY_STATE_STORE_NAME, Duration.ofDays(7), Duration.ofSeconds(1), false),
            Serdes.String(),
            JsonSerde.of(ExecutionDelay.class)
        ));

        // declare common stream
        KStream<String, WorkerTaskResult> workerTaskResultKStream = this.workerTaskResultKStream(builder);
        KStream<String, Executor> executorKStream = this.executorKStream(builder);

        // join with killed
        KStream<String, ExecutionKilled> executionKilledKStream = this.executionKilledKStream(builder);
        KStream<String, Executor> executionWithKilled = this.joinExecutionKilled(executionKilledKStream, executorKStream);

        // join with WorkerResult
        KStream<String, Executor> executionKStream = this.joinWorkerResult(workerTaskResultKStream, executionWithKilled);

        // handle state on execution
        KStream<String, Executor> stream = kafkaStreamSourceService.executorWithFlow(executionKStream, true);

        stream = this.handleExecutor(stream);

        // save execution
        this.toExecution(stream, "Main");
        this.toWorkerTask(stream);
        this.handleExecutionDelay(stream);
        this.toWorkerTaskResult(stream);

        this.toExecutorFlowTriggerTopic(stream);

        // task Flow
        KTable<String, WorkerTaskExecution> workerTaskExecutionKTable = this.workerTaskExecutionStream(builder);

        KStream<String, WorkerTaskExecution> workerTaskExecutionKStream = this.deduplicateWorkerTaskExecution(stream);
        this.toWorkerTaskExecution(workerTaskExecutionKStream);
        this.workerTaskExecutionToExecution(workerTaskExecutionKStream);
        this.handleWorkerTaskExecution(workerTaskExecutionKTable, stream);

        // purge at end
        this.purgeExecutor(stream);

        this.purgeWorkerRunning(workerTaskResultKStream);

        return builder;
    }

    public KStream<String, Executor> executorKStream(StreamsBuilder builder) {
        KStream<String, Executor> result = builder
            .stream(
                kafkaAdminService.getTopicName(Execution.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("Executor.fromExecution")
            )
            .filter((key, value) -> value != null, Named.as("Executor.filterNotNull"))
            .transformValues(
                () -> new ExecutorFromExecutionTransformer(EXECUTOR_STATE_STORE_NAME),
                Named.as("Executor.toExecutor"),
                EXECUTOR_STATE_STORE_NAME
            );

        // logs
        KafkaStreamSourceService.logIfEnabled(
            log,
            result,
            (key, value) -> executorService.log(log, true, value),
            "ExecutionIn"
        );

        return result;
    }

    private KStream<String, ExecutionKilled> executionKilledKStream(StreamsBuilder builder) {
        return builder
            .stream(
                kafkaAdminService.getTopicName(ExecutionKilled.class),
                Consumed.with(Serdes.String(), JsonSerde.of(ExecutionKilled.class)).withName("KTable.ExecutionKilled")
            );
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
                    this.executorService,
                    this.kafkaStreamSourceService,
                    this.metricRegistry
                ),
                Named.as("JoinWorkerResult.transformValues"),
                EXECUTOR_STATE_STORE_NAME
            )
            .filter(
                (key, value) -> value != null,
                Named.as("JoinWorkerResult.notNullFilter")
            );
    }

    private KStream<String, Executor> handleExecutor(KStream<String, Executor> stream) {
        return stream
            .transformValues(
                () -> new ExecutorNextTransformer(
                    NEXTS_DEDUPLICATION_STATE_STORE_NAME,
                    this.executorService
                ),
                Named.as("HandleExecutor.transformValues"),
                NEXTS_DEDUPLICATION_STATE_STORE_NAME
            );
    }

    private void purgeExecutor(KStream<String, Executor> stream) {
        KStream<String, Executor> terminated = stream
            .filter(
                (key, value) -> executorService.canBePurged(value),
                Named.as("PurgeExecutor.filterTerminated")
            );

        // clean up executor
        terminated
            .mapValues(
                (readOnlyKey, value) -> (Execution) null,
                Named.as("PurgeExecutor.executionToNull")
            )
            .to(
                kafkaAdminService.getTopicName(Executor.class),
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

        taskRunKStream
            .transformValues(
                () -> new DeduplicationPurgeTransformer<>(
                    WORKERTASK_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> "WorkerTaskExecution-" + value.getExecutionId() + "-" + value.getId()
                ),
                Named.as("PurgeExecutor.purgeWorkerTaskExecutionDeduplication"),
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
        terminated
            .filter(
                (key, value) -> value.getExecution().getState().getCurrent() == State.Type.KILLED,
                Named.as("PurgeExecutor.filterKilledToNull")
            )
            .mapValues(
                (readOnlyKey, value) -> (ExecutionKilled) null,
                Named.as("PurgeExecutor.executionKilledToNull")
            )
            .to(
                kafkaAdminService.getTopicName(ExecutionKilled.class),
                Produced.with(Serdes.String(), JsonSerde.of(ExecutionKilled.class)).withName("PurgeExecutor.toExecutionKilled")
            );
    }

    private void toExecutorFlowTriggerTopic(KStream<String, Executor> stream) {
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
                () -> new FlowWithTriggerTransformer(kafkaFlowExecutor, flowService),
                Named.as("HandleExecutorFlowTriggerTopic.flatMapToExecutorFlowTrigger")
            )
            .to(
                kafkaAdminService.getTopicName(io.kestra.runner.kafka.streams.ExecutorFlowTrigger.class),
                Produced
                    .with(Serdes.String(), JsonSerde.of(io.kestra.runner.kafka.streams.ExecutorFlowTrigger.class))
                    .withName("PurgeExecutor.toExecutorFlowTrigger")
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
            log,
            resultFlowable,
            (key, value) -> executorService.log(log, false, value),
            "HandleWorkerTaskFlowable"
        );

        workerTaskResultKStream
            .to(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class)).withName("HandleWorkerTaskFlowable.toWorkerTaskResult")
            );

        // not flowable > to WorkerTask
        KStream<String, WorkerTask> resultNotFlowable = dedupWorkerTask
            .filter((key, value) -> value.getTask().isSendToWorkerTask(), Named.as("HandleWorkerTaskNotFlowable.filterIsNotFlowable"))
            .map((key, value) -> new KeyValue<>(queueService.key(value), value), Named.as("HandleWorkerTaskNotFlowable.mapWithKey"))
            .selectKey(
                (key, value) -> queueService.key(value),
                Named.as("HandleWorkerTaskNotFlowable.selectKey")
            );

        KStream<String, WorkerTask> workerTaskKStream = KafkaStreamSourceService.logIfEnabled(
            log,
            resultNotFlowable,
            (key, value) -> executorService.log(log, false, value),
            "HandleWorkerTaskNotFlowable"
        );

        workerTaskKStream
            .to(
                kafkaAdminService.getTopicName(WorkerTask.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTask.class)).withName("HandleWorkerTaskNotFlowable.toWorkerTask")
            );
    }

    private KTable<String, WorkerTaskExecution> workerTaskExecutionStream(StreamsBuilder builder) {
        return builder
            .table(
                kafkaAdminService.getTopicName(WorkerTaskExecution.class),
                Consumed.with(Serdes.String(), JsonSerde.of(WorkerTaskExecution.class)).withName("WorkerTaskExecution.from"),
                Materialized.<String, WorkerTaskExecution, KeyValueStore<Bytes, byte[]>>as("workertaskexecution")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(WorkerTaskExecution.class))
            );
    }

    private KStream<String, WorkerTaskExecution> deduplicateWorkerTaskExecution(KStream<String, Executor> stream) {
        return stream
            .flatMapValues(
                (readOnlyKey, value) -> value.getWorkerTaskExecutions(),
                Named.as("DeduplicateWorkerTaskExecution.flatMap")
            )
            .transformValues(
                () -> new DeduplicationTransformer<>(
                    "DeduplicateWorkerTaskExecution",
                    WORKERTASK_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> "WorkerTaskExecution-" + value.getTaskRun().getExecutionId() + "-" + value.getTaskRun().getId(),
                    (key, value) -> value.getTaskRun().getState().getCurrent().name()
                ),
                Named.as("DeduplicateWorkerTaskExecution.deduplication"),
                WORKERTASK_DEDUPLICATION_STATE_STORE_NAME
            )
            .filter((key, value) -> value != null, Named.as("DeduplicateWorkerTaskExecution.notNullFilter"));
    }

    private void toWorkerTaskExecution(KStream<String, WorkerTaskExecution> stream) {
        stream
            .selectKey(
                (key, value) -> value.getExecution().getId(),
                Named.as("ToWorkerTaskExecution.selectKey")
            )
            .to(
                kafkaAdminService.getTopicName(WorkerTaskExecution.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskExecution.class)).withName("ToWorkerTaskExecution.toWorkerTaskExecution")
            );
    }

    private void workerTaskExecutionToExecution(KStream<String, WorkerTaskExecution> stream) {
        stream
            .mapValues(
                value -> {
                    String message = "Create new execution for flow '" +
                        value.getExecution().getNamespace() + "'." + value.getExecution().getFlowId() +
                        "' with id '" + value.getExecution().getId() + "' from task '" + value.getTask().getId() +
                        "' and taskrun '" + value.getTaskRun().getId() +
                        (value.getTaskRun().getValue() != null  ? " (" +  value.getTaskRun().getValue() + ")" : "") + "'";

                    log.info(message);

                    LogEntry.LogEntryBuilder logEntryBuilder = LogEntry.of(value.getTaskRun()).toBuilder()
                        .level(Level.INFO)
                        .message(message)
                        .timestamp(value.getTaskRun().getState().getStartDate())
                        .thread(Thread.currentThread().getName());

                    return logEntryBuilder.build();
                },
                Named.as("WorkerTaskExecutionToExecution.mapToLog")
            )
            .selectKey((key, value) -> (String)null, Named.as("WorkerTaskExecutionToExecution.logRemoveKey"))
            .to(
                kafkaAdminService.getTopicName(LogEntry.class),
                Produced.with(Serdes.String(), JsonSerde.of(LogEntry.class)).withName("WorkerTaskExecutionToExecution.toLogEntry")
            );

        KStream<String, Execution> executionKStream = stream
            .mapValues(
                (key, value) -> value.getExecution(),
                Named.as("WorkerTaskExecutionToExecution.map")
            )
            .selectKey(
                (key, value) -> value.getId(),
                Named.as("WorkerTaskExecutionToExecution.selectKey")
            );

        executionKStream = KafkaStreamSourceService.logIfEnabled(
            log,
            executionKStream,
            (key, value) -> executorService.log(log, false, value),
            "WorkerTaskExecutionToExecution"
        );

        executionKStream
            .to(
                kafkaAdminService.getTopicName(Execution.class),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("WorkerTaskExecutionToExecution.toExecution")
            );
    }

    private void handleWorkerTaskExecution(KTable<String, WorkerTaskExecution> workerTaskExecutionKTable, KStream<String, Executor> stream) {
        KStream<String, WorkerTaskResult> joinKStream = stream
            .filter(
                (key, value) -> conditionService.isTerminatedWithListeners(value.getFlow(), value.getExecution()),
                Named.as("HandleWorkerTaskExecution.isTerminated")
            )
            .transformValues(
                () -> new WorkerTaskExecutionTransformer(runContextFactory, workerTaskExecutionKTable.queryableStoreName(), kafkaFlowExecutor),
                 Named.as("HandleWorkerTaskExecution.transform"),
                workerTaskExecutionKTable.queryableStoreName()
            )
            .filter((key, value) -> value != null, Named.as("HandleWorkerTaskExecution.joinNotNullFilter"));

        toWorkerTaskResultSend(joinKStream, "HandleWorkerTaskExecution");
    }

    private void handleExecutionDelay(KStream<String, Executor> stream) {
        KStream<String, Executor> executionDelayStream = stream
            .flatMapValues(
                (readOnlyKey, value) -> value.getExecutionDelays(),
                Named.as("HandleExecutionDelay.flapMap")
            )
            .transform(
                () -> new ExecutorPausedTransformer(EXECUTION_DELAY_STATE_STORE_NAME, EXECUTOR_STATE_STORE_NAME, executionService),
                Named.as("HandleExecutionDelay.transform"),
                EXECUTION_DELAY_STATE_STORE_NAME,
                EXECUTOR_STATE_STORE_NAME
            )
            .filter((key, value) -> value != null, Named.as("HandleExecutionDelay.notNullFilter"));

        toExecution(executionDelayStream, "Delay");
    }

    private void toWorkerTaskResult(KStream<String, Executor> stream) {
        KStream<String, WorkerTaskResult> workerTaskResultKStream = stream
            .flatMapValues(
                (readOnlyKey, value) -> value.getWorkerTaskResults(),
                Named.as("ToWorkerTaskResult.flapMap")
            );

        toWorkerTaskResultSend(workerTaskResultKStream, "HandleWorkerTaskResult");
    }

    private void toWorkerTaskResultSend(KStream<String, WorkerTaskResult> stream, String name) {
        KStream<String, WorkerTaskResult> workerTaskResultKStream = stream
            .transformValues(
                () -> new DeduplicationTransformer<>(
                    name,
                    WORKERTASK_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> value.getTaskRun().getExecutionId() + "-" + value.getTaskRun().getId(),
                    (key, value) -> value.getTaskRun().getState().getCurrent().name()
                ),
                Named.as(name + ".deduplication"),
                WORKERTASK_DEDUPLICATION_STATE_STORE_NAME
            )
            .filter((key, value) -> value != null, Named.as(name + ".notNullFilter"))
            .selectKey(
                (key, value) -> value.getTaskRun().getId(),
                Named.as(name + ".selectKey")
            );

        KafkaStreamSourceService.logIfEnabled(
            log,
            workerTaskResultKStream,
            (key, value) -> executorService.log(log, false, value),
            name
        )
            .to(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class)).withName(name + ".toWorkerTaskResult")
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

    private void toExecution(KStream<String, Executor> stream, String name) {
        KStream<String, Executor> streamFrom = stream
            .filter((key, value) -> value.isExecutionUpdated(), Named.as(name + "ToExecution.haveFrom"))
            .transformValues(
                ExecutorAddHeaderTransformer::new,
                Named.as(name + "ToExecution.addHeaders")
            );

        // send execution
        KStream<String, Executor> executionKStream = streamFrom
            .filter((key, value) -> value.getException() == null, Named.as(name + "ToExecutionExecution.notException"));

        toExecutionSend(executionKStream, name + "ToExecutionExecution");

        // send exception
        KStream<String, Pair<Executor, Execution.FailedExecutionWithLog>> failedStream = streamFrom
            .filter((key, value) -> value.getException() != null, Named.as(name + "ToExecutionException.isException"))
            .mapValues(
                e -> Pair.of(e, e.getExecution().failedExecutionFromExecutor(e.getException())),
                Named.as(name + "ToExecutionException.mapToFailedExecutionWithLog")
            );

        failedStream
            .flatMapValues(e -> e.getRight().getLogs(), Named.as(name + "ToExecutionException.flatmapLogs"))
            .selectKey((key, value) -> (String)null, Named.as(name + "ToExecutionException.removeKey"))
            .to(
                kafkaAdminService.getTopicName(LogEntry.class),
                Produced.with(Serdes.String(), JsonSerde.of(LogEntry.class)).withName(name + "ToExecutionException.toLogEntry")
            );

        KStream<String, Executor> executorFailedKStream = failedStream
            .mapValues(
                e -> e.getLeft().withExecution(e.getRight().getExecution(), "failedExecutionFromExecutor"),
                Named.as(name + "ToExecutionException.mapToExecutor")
            );

        toExecutionSend(executorFailedKStream, name + "ToExecutionException");
    }

    private void toExecutionSend(KStream<String, Executor> stream, String from) {
        stream = KafkaStreamSourceService.logIfEnabled(
            log,
            stream,
            (key, value) -> executorService.log(log, false, value),
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
}
