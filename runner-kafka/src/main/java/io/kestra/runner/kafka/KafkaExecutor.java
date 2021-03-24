package io.kestra.runner.kafka;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Prototype;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.Stores;
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
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.*;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.core.utils.Either;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.kestra.runner.kafka.services.KafkaAdminService;
import io.kestra.runner.kafka.services.KafkaStreamService;
import io.kestra.runner.kafka.services.KafkaStreamSourceService;
import io.kestra.runner.kafka.services.KafkaStreamsBuilder;
import io.kestra.runner.kafka.streams.*;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;

@KafkaQueueEnabled
@Prototype
@Slf4j
public class KafkaExecutor extends AbstractExecutor implements Closeable {
    private static final String WORKERTASK_DEDUPLICATION_STATE_STORE_NAME = "workertask_deduplication";
    private static final String TRIGGER_DEDUPLICATION_STATE_STORE_NAME = "trigger_deduplication";
    public static final String TRIGGER_MULTIPLE_STATE_STORE_NAME = "trigger_multiplecondition";
    private static final String NEXTS_DEDUPLICATION_STATE_STORE_NAME = "next_deduplication";
    public static final String WORKER_RUNNING_STATE_STORE_NAME = "worker_running";
    public static final String WORKERINSTANCE_STATE_STORE_NAME = "worker_instance";
    public static final String TOPIC_EXECUTOR_WORKERINSTANCE = "executorworkerinstance";

    ApplicationContext applicationContext;
    KafkaStreamService kafkaStreamService;
    KafkaAdminService kafkaAdminService;
    QueueInterface<LogEntry> logQueue;
    FlowService flowService;
    KafkaStreamSourceService kafkaStreamSourceService;
    QueueService queueService;

    KafkaStreamService.Stream resultStream;
    boolean ready = false;

    @Inject
    public KafkaExecutor(
        ApplicationContext applicationContext,
        RunContextFactory runContextFactory,
        KafkaStreamService kafkaStreamService,
        KafkaAdminService kafkaAdminService,
        @javax.inject.Named(QueueFactoryInterface.WORKERTASKLOG_NAMED) QueueInterface<LogEntry> logQueue,
        MetricRegistry metricRegistry,
        FlowService flowService,
        ConditionService conditionService,
        KafkaStreamSourceService kafkaStreamSourceService,
        TaskDefaultService taskDefaultService,
        QueueService queueService
    ) {
        super(runContextFactory, metricRegistry, conditionService, taskDefaultService);

        this.applicationContext = applicationContext;
        this.kafkaStreamService = kafkaStreamService;
        this.kafkaAdminService = kafkaAdminService;
        this.logQueue = logQueue;
        this.flowService = flowService;
        this.kafkaStreamSourceService = kafkaStreamSourceService;
        this.queueService = queueService;
    }

    public Topology topology() {
        StreamsBuilder builder = new KafkaStreamsBuilder();

        // copy execution to be done on executor queue
        this.executionToExecutor(builder);

        // worker deduplication
        builder.addStateStore(Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(WORKERTASK_DEDUPLICATION_STATE_STORE_NAME),
            Serdes.String(),
            Serdes.String()
        ));

        // next deduplication
        builder.addStateStore(Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(NEXTS_DEDUPLICATION_STATE_STORE_NAME),
            Serdes.String(),
            JsonSerde.of(ExecutionNextsDeduplicationTransformer.Store.class)
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
            Consumed.with(Serdes.String(), JsonSerde.of(WorkerInstance.class)),
            () -> new GlobalStateProcessor<>(WORKERINSTANCE_STATE_STORE_NAME)
        );

        // declare ktable & kstream
        KStream<String, WorkerTaskResult> workerTaskResultKStream = this.workerTaskResultKStream(builder);
        KTable<String, Execution> executorKTable = kafkaStreamSourceService.executorKTable(builder);
        KTable<String, Execution> executionNotKilledKTable = this.joinExecutionKilled(builder, executorKTable);
        KTable<String, WorkerTaskResultState> workerTaskResultKTable = this.workerTaskResultKTable(workerTaskResultKStream);
        GlobalKTable<String, Flow> flowKTable = kafkaStreamSourceService.flowGlobalKTable(builder);
        GlobalKTable<String, WorkerTaskRunning> workerTaskRunningKTable = this.workerTaskRunningKStream(builder);
        KStream<String, WorkerInstance> workerInstanceKStream = this.workerInstanceKStream(builder);
        KStream<String, ExecutorFlowTrigger> flowWithTriggerStream = this.flowWithTriggerStream(builder);
        this.templateKTable(builder);

        // logs
        KafkaStreamSourceService.logIfEnabled(
            executorKTable.toStream(Named.as("execution-toStream")),
            (key, value) -> log.debug(
                "Execution in '{}' with checksum '{}': {}",
                value.getId(),
                value.toCrc32State(),
                value.toStringState()
            ),
            "execution-in"
        );

        // join with worker result
        KStream<String, Execution> executionKStream = this.joinWorkerResult(workerTaskResultKTable, executionNotKilledKTable);

        // handle state on execution
        KStream<String, ExecutionWithFlow> stream = kafkaStreamSourceService.withFlow(flowKTable, executionKStream);

        this.handleMain(stream);
        this.handleNexts(stream);

        this.handleWorkerTask(stream);
        this.handleWorkerTaskResult(stream);

        // trigger
        this.handleExecutorFlowTriggerTopic(stream);
        this.handleFlowTrigger(flowWithTriggerStream);

        this.purgeExecutor(stream);

        // handle worker
        this.purgeWorkerRunning(workerTaskResultKStream);
        this.detectNewWorker(workerInstanceKStream, workerTaskRunningKTable);

        // build
        Topology topology = builder.build();

        if (log.isTraceEnabled()) {
            log.trace(topology.describe().toString());
        }

        return topology;
    }

    private void executionToExecutor(StreamsBuilder builder) {
        builder
            .stream(
                kafkaAdminService.getTopicName(Execution.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class))
            )
            .filter((key, value) -> value.getTaskRunList() == null ||
                value.getTaskRunList().size() == 0 ||
                value.isJustRestarted(),
                Named.as("executionToExecutor")
            )
            .to(
                kafkaAdminService.getTopicName(KafkaStreamSourceService.TOPIC_EXECUTOR),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
            );
    }


    private GlobalKTable<String, Template> templateKTable(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(Template.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Template.class)),
                Materialized.<String, Template, KeyValueStore<Bytes, byte[]>>as("template")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Template.class))
            );
    }

    private KTable<String, ExecutionKilled> executionKilledKTable(StreamsBuilder builder) {
        return builder
            .table(
                kafkaAdminService.getTopicName(ExecutionKilled.class),
                Consumed.with(Serdes.String(), JsonSerde.of(ExecutionKilled.class)),
                Materialized.<String, ExecutionKilled, KeyValueStore<Bytes, byte[]>>as("execution_killed")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(ExecutionKilled.class))
            );
    }

    private GlobalKTable<String, WorkerTaskRunning> workerTaskRunningKStream(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(WorkerTaskRunning.class),
                Consumed.with(Serdes.String(), JsonSerde.of(WorkerTaskRunning.class)),
                Materialized.<String, WorkerTaskRunning, KeyValueStore<Bytes, byte[]>>as(WORKER_RUNNING_STATE_STORE_NAME)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(WorkerTaskRunning.class))
            );
    }

    private KStream<String, WorkerInstance> workerInstanceKStream(StreamsBuilder builder) {
        return builder
            .stream(
                kafkaAdminService.getTopicName(WorkerInstance.class),
                Consumed.with(Serdes.String(), JsonSerde.of(WorkerInstance.class))
            );
    }

    private KStream<String, ExecutorFlowTrigger> flowWithTriggerStream(StreamsBuilder builder) {
        return builder
            .stream(
                kafkaAdminService.getTopicName(ExecutorFlowTrigger.class),
                Consumed.with(Serdes.String(), JsonSerde.of(ExecutorFlowTrigger.class))
            )
            .filter((key, value) -> value != null, Named.as("flowWithTriggerStream-notNull-filter"));
    }

    private KTable<String, Execution> joinExecutionKilled(StreamsBuilder builder, KTable<String, Execution> executionKTable) {
        KTable<String, HasKilledJoin> table = executionKTable
            .leftJoin(
                this.executionKilledKTable(builder),
                (execution, executionKilled) -> {
                    if (executionKilled != null &&
                        execution.getState().getCurrent() != State.Type.KILLING &&
                        !execution.getState().isTerninated()
                    ) {
                        Execution newExecution = execution.withState(State.Type.KILLING);

                        if (log.isDebugEnabled()) {
                            log.debug("Killed in: {}", newExecution.toStringState());
                        }

                        return new HasKilledJoin(newExecution, true);
                    }

                    return new HasKilledJoin(execution, false);
                },
                Named.as("executionKilled-leftJoin")
            );

        KStream<String, Execution> join = table
            .filter((key, value) -> value.isJoined(), Named.as("executionKilled-isJoined-filter"))
            .mapValues((readOnlyKey, value) -> value.getExecution(), Named.as("executionKilled-isJoined-map"))
            .toStream();

        toExecution(join, "executionKilled");

        return table
            .filter((key, value) -> !value.isJoined(), Named.as("executionKilled-isNotJoined-filter"))
            .mapValues((readOnlyKey, value) -> value.getExecution(), Named.as("executionKilled-isNotJoined-map"));
    }

    private KStream<String, WorkerTaskResult> workerTaskResultKStream(StreamsBuilder builder) {
        return builder
            .stream(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Consumed.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class))
            )
            .filter((key, value) -> value != null, Named.as("workerTaskResultKStream-null-filter"));
    }

    private KTable<String, WorkerTaskResultState> workerTaskResultKTable(KStream<String, WorkerTaskResult> workerTaskResultKStream) {
        return workerTaskResultKStream
            .groupBy(
                (key, value) -> value.getTaskRun().getExecutionId(),
                Grouped.<String, WorkerTaskResult>as("workertaskresult_groupby")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(WorkerTaskResult.class))
            )
            .aggregate(
                WorkerTaskResultState::new,
                (key, newValue, aggregate) -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Aggregate in: {}", newValue.getTaskRun().toStringState());
                    }

                    aggregate
                        .getResults()
                        .compute(
                            newValue.getTaskRun().getId(),
                            (s, workerTaskResult) -> newValue
                        );

                    return aggregate;
                },
                Named.as("workerTaskResultKTable-aggregate"),
                Materialized.<String, WorkerTaskResultState, KeyValueStore<Bytes, byte[]>>as("workertaskresult")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(WorkerTaskResultState.class))
            );
    }

    private KStream<String, Execution> joinWorkerResult(KTable<String, WorkerTaskResultState> workerTaskResultKTable, KTable<String, Execution> executionKTable) {
        KStream<String, Either<WithException, HasWorkerResultJoin>> eitherKStream = executionKTable
            .leftJoin(
                workerTaskResultKTable,
                (execution, workerTaskResultState) -> {
                    if (workerTaskResultState == null) {
                        return Either.<WithException, HasWorkerResultJoin>right(new HasWorkerResultJoin(execution, false));
                    }

                    return workerTaskResultState
                        .getResults()
                        .values()
                        .stream()
                        .filter(workerTaskResult -> execution.hasTaskRunJoinable(workerTaskResult.getTaskRun()))
                        .findFirst()
                        .map(workerTaskResult -> {
                            if (log.isDebugEnabled()) {
                                log.debug("WorkerTaskResult in: {}", workerTaskResult.getTaskRun().toStringState());
                            }

                            metricRegistry
                                .counter(MetricRegistry.KESTRA_EXECUTOR_TASKRUN_ENDED_COUNT, metricRegistry.tags(workerTaskResult))
                                .increment();

                            metricRegistry
                                .timer(MetricRegistry.KESTRA_EXECUTOR_TASKRUN_ENDED_DURATION, metricRegistry.tags(workerTaskResult))
                                .record(workerTaskResult.getTaskRun().getState().getDuration());

                            try {
                                return Either.<WithException, HasWorkerResultJoin>right(new HasWorkerResultJoin(
                                    execution.withTaskRun(workerTaskResult.getTaskRun()),
                                    true
                                ));
                            } catch (Exception e) {
                                return Either.<WithException, HasWorkerResultJoin>left(new WithException(new HasWorkerResultJoin(execution, false), e));
                            }
                        })
                        .orElseGet(() -> Either.<WithException, HasWorkerResultJoin>right(new HasWorkerResultJoin(execution, false)));
                },
                Named.as("join-leftJoin")
            )
            .toStream(Named.as("join-toStream"));

        KStream<String, HasWorkerResultJoin> join = branchException(eitherKStream, "join");

        return toExecutionJoin(join);
    }

    private void handleMain(KStream<String, ExecutionWithFlow> stream) {
        KStream<String, Execution> result = stream
            .mapValues(
                (readOnlyKey, value) -> {
                    Optional<Execution> main = this.doMain(value.getExecution(), value.getFlow());

                    return main.orElse(null);
                },
                Named.as("handleMain-map")
            );

        this.toExecution(result, "handleMain");
    }

    private void purgeExecutor(KStream<String, ExecutionWithFlow> stream) {
        KStream<String, ExecutionWithFlow> terminatedWithKilled = stream
            .filter(
                (key, value) -> conditionService.isTerminatedWithListeners(value.getFlow(), value.getExecution()),
                Named.as("purgeExecutor-terminated-filter")
            );

        // we don't purge killed execution in order to have feedback about child running tasks
        // this can be killed lately (after the executor kill the execution), but we want to keep
        // feedback about the actual state (killed or not)
        // @TODO: this can lead to infinite state store for most executor topic
        KStream<String, ExecutionWithFlow> terminated = terminatedWithKilled.filter(
            (key, value) -> value.getExecution().getState().getCurrent() != State.Type.KILLED,
            Named.as("purgeExecutor-notkilled-filter")
        );

        // clean up executor
        terminated
            .mapValues(
                (readOnlyKey, value) -> (Execution) null,
                Named.as("purgeExecutor-executorToNull-map")
            )
            .to(
                kafkaAdminService.getTopicName(KafkaStreamSourceService.TOPIC_EXECUTOR),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
            );

        // flatMap taskRun
        KStream<String, TaskRun> taskRunKStream = terminated
            .filter(
                (key, value) -> value.getExecution().getTaskRunList() != null,
                Named.as("purgeExecutor-nullValue-filter")
            )
            .flatMapValues(
                (readOnlyKey, value) -> value.getExecution().getTaskRunList(),
                Named.as("purgeExecutor-toTaskRun-flatmap")
            );

        // clean up workerTaskResult
        taskRunKStream
            .map(
                (readOnlyKey, value) -> new KeyValue<>(
                    value.getId(),
                    (WorkerTaskResult) null
                ),
                Named.as("purgeExecutor-workerTaskToNull-map")
            )
            .to(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class))
            );

        // clean up WorkerTask deduplication state
        taskRunKStream
            .transformValues(
                () -> new DeduplicationPurgeTransformer<>(
                    WORKERTASK_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> value.getExecutionId() + "-" + value.getId()
                ),
                Named.as("purgeExecutor-workerTaskPurge-transform"),
                WORKERTASK_DEDUPLICATION_STATE_STORE_NAME
            );

        // clean up Execution Nexts deduplication state
        terminated
            .transformValues(
                () -> new DeduplicationPurgeTransformer<>(
                    NEXTS_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> value.getExecution().getId()
                ),
                Named.as("executionNexts-nextsPurge-transform"),
                NEXTS_DEDUPLICATION_STATE_STORE_NAME
            );

        // clean up killed
        terminatedWithKilled
            .mapValues(
                (readOnlyKey, value) -> (ExecutionKilled) null,
                Named.as("purgeExecutor-killedPurge-map")
            )
            .to(
                kafkaAdminService.getTopicName(ExecutionKilled.class),
                Produced.with(Serdes.String(), JsonSerde.of(ExecutionKilled.class))
            );
    }

    private void handleExecutorFlowTriggerTopic(KStream<String, ExecutionWithFlow> stream) {
        stream
            .filter(
                (key, value) -> conditionService.isTerminatedWithListeners(value.getFlow(), value.getExecution()),
                Named.as("handleExecutorFlowTriggerTopic-terminated-filter")
            )
            .transformValues(
                () -> new DeduplicationTransformer<>(
                    TRIGGER_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> value.getExecution().getId(),
                    (key, value) -> value.getExecution().getId()
                ),
                Named.as("handleExecutorFlowTriggerTopic-deduplication-transform"),
                TRIGGER_DEDUPLICATION_STATE_STORE_NAME
            )
            .filter((key, value) -> value != null, Named.as("handleExecutorFlowTriggerTopic-dedupNull-filter"))
            .flatTransform(
                () -> new FlowWithTriggerTransformer(
                    flowService
                ),
                Named.as("handleExecutorFlowTriggerTopic-trigger-transform")
            )
            .to(
                kafkaAdminService.getTopicName(ExecutorFlowTrigger.class),
                Produced.with(Serdes.String(), JsonSerde.of(ExecutorFlowTrigger.class))
            );
    }

    private void handleFlowTrigger(KStream<String, ExecutorFlowTrigger> stream) {
        stream
            .transformValues(
                () -> new FlowTriggerWithExecutionTransformer(
                    TRIGGER_MULTIPLE_STATE_STORE_NAME,
                    flowService
                ),
                Named.as("handleFlowTrigger-trigger-transform"),
                TRIGGER_MULTIPLE_STATE_STORE_NAME
            )
            .flatMap(
                (key, value) -> value
                    .stream()
                    .map(execution -> new KeyValue<>(execution.getId(), execution))
                    .collect(Collectors.toList()),
                Named.as("handleFlowTrigger-execution-flapMap")
            )
            .to(
                kafkaAdminService.getTopicName(Execution.class),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
            );

        stream
            .mapValues((readOnlyKey, value) -> (ExecutorFlowTrigger)null)
            .to(
                kafkaAdminService.getTopicName(ExecutorFlowTrigger.class),
                Produced.with(Serdes.String(), JsonSerde.of(ExecutorFlowTrigger.class))
            );
    }

    private void handleNexts(KStream<String, ExecutionWithFlow> stream) {
        KStream<String, Either<WithException, ExecutionNexts>> streamEither = stream
            .mapValues(
                (readOnlyKey, value) -> {
                    try {
                        Optional<List<TaskRun>> nexts = this.doNexts(value.getExecution(), value.getFlow());

                        return Either.right(new ExecutionNexts(
                            value.getFlow(),
                            value.getExecution(),
                            nexts.orElse(null)
                        ));
                    } catch (Exception e) {
                        return Either.left(new WithException(value, e));
                    }
                },
                Named.as("handleNexts-map")
            );

        KStream<String, Execution> result = branchException(streamEither, "handleNexts")
            .transformValues(
                () -> new ExecutionNextsDeduplicationTransformer(
                    NEXTS_DEDUPLICATION_STATE_STORE_NAME
                ),
                Named.as("handleNexts-deduplication-transform"),
                NEXTS_DEDUPLICATION_STATE_STORE_NAME
            )
            .filter((key, value) -> value != null, Named.as("handleNexts-dedupNull-filter"))
            .mapValues(
                (readOnlyKey, value) -> this.onNexts(value.getFlow(), value.getExecution(), value.getNexts()),
                Named.as("handleNexts-onNexts-map")
            );

        this.toExecution(result, "handleChildNext");
    }

    private void handleWorkerTask(KStream<String, ExecutionWithFlow> stream) {
        KStream<String, Either<WithException, List<WorkerTask>>> streamEither = stream
            .mapValues(
                (readOnlyKey, value) -> {
                    try {
                        return Either.right(
                            this.doWorkerTask(value.getExecution(), value.getFlow()).orElse(null)
                        );
                    } catch (Exception e) {
                        return Either.left(new WithException(value, e));
                    }
                },
                Named.as("handleWorkerTask-map")
            );

        KStream<String, WorkerTask> dedupWorkerTask = branchException(streamEither, "handleWorkerTask")
            .flatMapValues((readOnlyKey, value) -> value)
            .transformValues(
                () -> new DeduplicationTransformer<>(
                    WORKERTASK_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> value.getTaskRun().getExecutionId() + "-" + value.getTaskRun().getId(),
                    (key, value) -> value.getTaskRun().getState().getCurrent().name()
                ),
                Named.as("handleWorkerTask-deduplication-transform"),
                WORKERTASK_DEDUPLICATION_STATE_STORE_NAME
            )
            .filter((key, value) -> value != null, Named.as("handleWorkerTask-null-filter"));

        KStream<String, WorkerTaskResult> resultFlowable = dedupWorkerTask
            .filter((key, value) -> value.getTask().isFlowable(), Named.as("handleWorkerTask-isFlowable-filter"))
            .mapValues(
                (key, value) -> new WorkerTaskResult(value.withTaskRun(value.getTaskRun().withState(State.Type.RUNNING))),
                Named.as("handleWorkerTask-isFlowableToRunning-mapValues")
            )
            .map(
                (key, value) -> new KeyValue<>(queueService.key(value), value),
                Named.as("handleWorkerTask-isFlowable-map")
            )
            .selectKey(
                (key, value) -> queueService.key(value),
                Named.as("handleWorkerTask-isFlowable-selectKey")
            );

        KStream<String, WorkerTaskResult> workerTaskResultKStream = KafkaStreamSourceService.logIfEnabled(
            resultFlowable,
            (key, value) -> log.debug(
                "WorkerTaskResult out: {}",
                value.getTaskRun().toStringState()
            ),
            "handleWorkerTask-isFlowableLog"
        );

        workerTaskResultKStream
            .to(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class))
            );

        KStream<String, WorkerTask> resultNotFlowable = dedupWorkerTask
            .filter((key, value) -> !value.getTask().isFlowable(), Named.as("handleWorkerTask-notFlowable-filter"))
            .map((key, value) -> new KeyValue<>(queueService.key(value), value), Named.as("handleWorkerTask-notFlowableKeyValue-map"))
            .selectKey(
                (key, value) -> queueService.key(value),
                Named.as("handleWorkerTask-notFlowableKeyValue-selectKey")
            );

        KStream<String, WorkerTask> workerTaskKStream = KafkaStreamSourceService.logIfEnabled(
            resultNotFlowable,
            (key, value) -> log.debug(
                "WorkerTask out: {}",
                value.getTaskRun().toStringState()
            ),
            "handleWorkerTask-notFlowableLog"
        );

        workerTaskKStream
            .to(
                kafkaAdminService.getTopicName(WorkerTask.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTask.class))
            );
    }

    private void handleWorkerTaskResult(KStream<String, ExecutionWithFlow> stream) {
        KStream<String, Either<WithException, List<WorkerTaskResult>>> streamEither = stream
            .mapValues(
                (readOnlyKey, value) -> {
                    try {
                        return Either.right(
                            this.doWorkerTaskResult(value.getExecution(), value.getFlow()).orElse(null)
                        );
                    } catch (Exception e) {
                        return Either.left(new WithException(value, e));
                    }
                },
                Named.as("handleWorkerTaskResult-map")
            );

        branchException(streamEither, "handleWorkerTaskResult")
            .flatMapValues((readOnlyKey, value) -> value)
            .transformValues(
                () -> new DeduplicationTransformer<>(
                    WORKERTASK_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> value.getTaskRun().getExecutionId() + "-" + value.getTaskRun().getId(),
                    (key, value) -> value.getTaskRun().getState().getCurrent().name()
                ),
                Named.as("handleWorkerTaskResult-deduplication-transform"),
                WORKERTASK_DEDUPLICATION_STATE_STORE_NAME
            )
            .filter((key, value) -> value != null, Named.as("handleWorkerTaskResult-null-filter"))
            .selectKey(
                (key, value) -> value.getTaskRun().getId(),
                Named.as("handleWorkerTaskResult-selectKey")
            )
            .to(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class))
            );
    }

    private void purgeWorkerRunning(KStream<String, WorkerTaskResult> workerTaskResultKStream) {
        workerTaskResultKStream
            .filter((key, value) -> value.getTaskRun().getState().isTerninated(), Named.as("purgeWorkerRunning-terminated-filter"))
            .mapValues((readOnlyKey, value) -> (WorkerTaskRunning)null, Named.as("purgeWorkerRunning-toNull-mapValues"))
            .to(
                kafkaAdminService.getTopicName(WorkerTaskRunning.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskRunning.class))
            );
    }

    private void detectNewWorker(KStream<String, WorkerInstance> workerInstanceKStream, GlobalKTable<String, WorkerTaskRunning> workerTaskRunningGlobalKTable) {
        workerInstanceKStream
            .to(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR_WORKERINSTANCE),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerInstance.class))
            );

        KStream<String, WorkerInstanceTransformer.Result> stream = workerInstanceKStream
            .transformValues(
                WorkerInstanceTransformer::new,
                Named.as("detectNewWorker-transformValues")
            )
            .flatMapValues((readOnlyKey, value) -> value, Named.as("detectNewWorker-listToItem-flatMap"));

        // we resend the worker task from evicted worker
        KStream<String, WorkerTask> resultWorkerTask = stream
            .flatMapValues(
                (readOnlyKey, value) -> value.getWorkerTasksToSend(),
                Named.as("detectNewWorker-workerTask-flatMapValues")
            );

        // and remove from running since already sent
        resultWorkerTask
            .map((key, value) -> KeyValue.pair(value.getTaskRun().getId(), (WorkerTaskRunning)null), Named.as("detectNewWorker-runningToNull-map"))
            .to(
                kafkaAdminService.getTopicName(WorkerTaskRunning.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskRunning.class))
            );

        KafkaStreamSourceService.logIfEnabled(
            resultWorkerTask,
            (key, value) -> log.debug(
                "WorkerTask resend out: {}",
                value.getTaskRun().toStringState()
            ),
            "detectNewWorker-taskLog"
        )
            .to(
                kafkaAdminService.getTopicName(WorkerTask.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTask.class))
            );

        // we resend the WorkerInstance update
        KStream<String, WorkerInstance> updatedStream = KafkaStreamSourceService.logIfEnabled(
            stream,
            (key, value) -> log.debug(
                "Instance updated: {}",
                value
            ),
            "detectNewWorker-instanceLog"
        )
            .map(
                (key, value) -> value.getWorkerInstanceUpdated(),
                Named.as("detectNewWorker-instanceUpdate-map")
            );

        // cleanup executor workerinstance state store
        updatedStream
            .filter((key, value) -> value != null, Named.as("detectNewWorker-null-filter"))
            .to(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR_WORKERINSTANCE),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerInstance.class))
            );

        updatedStream
            .to(
                kafkaAdminService.getTopicName(WorkerInstance.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerInstance.class))
            );
    }

    private <T> KStream<String, T> branchException(KStream<String, Either<WithException, T>> stream, String methodName) {
        methodName = methodName + "-branchException";

        KStream<String, Execution.FailedExecutionWithLog> failedStream = stream
            .filter((key, value) -> value != null, Named.as(methodName + "-isLeftNotNull-filter"))
            .filter((key, value) -> value.isLeft(), Named.as(methodName + "-isLeft-filter"))
            .mapValues(Either::getLeft, Named.as(methodName + "-isLeft-map"))
            .mapValues(
                e -> e.getEntity().getExecution().failedExecutionFromExecutor(e.getException()),
                Named.as(methodName + "-isLeft-failedExecutionFromExecutor-map")
            );

        failedStream
            .flatMapValues(Execution.FailedExecutionWithLog::getLogs, Named.as(methodName + "-getLogs-flatMap"))
            .to(
                kafkaAdminService.getTopicName(LogEntry.class),
                Produced.with(Serdes.String(), JsonSerde.of(LogEntry.class))
            );

        KStream<String, Execution> result = failedStream
            .mapValues(Execution.FailedExecutionWithLog::getExecution, Named.as(methodName + "-getExecution-map"));

        this.toExecution(result, methodName + "-isLeft");

        return stream
            .filter((key, value) -> value != null, Named.as(methodName + "-isRightNotNull-filter"))
            .filter((key, value) -> value.isRight(), Named.as(methodName + "-isRight-filter"))
            .mapValues(Either::getRight, Named.as(methodName + "-isRight-map"))
            .filter((key, value) -> value != null, Named.as(methodName + "-isRight-notNull-filter"));
    }

    private KStream<String, Execution> toExecutionJoin(KStream<String, HasWorkerResultJoin> stream) {
        KStream<String, Execution> result = stream
            .filter((key, value) -> value != null, Named.as("join-isJoinedNull-filter"))
            .filter((key, value) -> value.isJoined(), Named.as("join-isJoined-filter"))
            .mapValues((readOnlyKey, value) -> value.getExecution(), Named.as("join-isJoined-map"));

        toExecution(result, "join");

        return stream
            .filter((key, value) -> value != null, Named.as("join-isNotJoinedNull-filter"))
            .filter((key, value) -> !value.isJoined(), Named.as("join-isNotJoined-filter"))
            .mapValues((readOnlyKey, value) -> value.getExecution(), Named.as("join-isNotJoined-map"));
    }

    private void toExecution(KStream<String, Execution> stream, String methodName) {
        methodName = methodName + "-toExecution";

        KStream<String, Execution> result = stream
            .filter((key, value) -> value != null, Named.as(methodName + "-null-filter"));

        KStream<String, Execution> executionKStream = KafkaStreamSourceService.logIfEnabled(
            result,
            (key, value) -> log.debug(
                "Execution out '{}' with checksum '{}': {}",
                value.getId(),
                value.toCrc32State(),
                value.toStringState()
            ),
            methodName + "-log"
        );

        executionKStream
            .to(
                kafkaAdminService.getTopicName(Execution.class),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
            );

        executionKStream
            .to(
                kafkaAdminService.getTopicName(KafkaStreamSourceService.TOPIC_EXECUTOR),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
            );
    }

    public interface ExecutionInterface {
        Execution getExecution();
    }

    @AllArgsConstructor
    @Getter
    public static class HasWorkerResultJoin implements ExecutionInterface {
        Execution execution;
        boolean joined;
    }

    @AllArgsConstructor
    @Getter
    public static class HasKilledJoin implements ExecutionInterface {
        Execution execution;
        boolean joined;
    }

    @AllArgsConstructor
    @Getter
    public static class ExecutionWithFlow implements ExecutionInterface {
        Flow flow;
        Execution execution;
    }

    @AllArgsConstructor
    @Getter
    public static class ExecutionNexts implements ExecutionInterface {
        Flow flow;
        Execution execution;
        List<TaskRun> nexts;
    }

    @AllArgsConstructor
    @Getter
    public static class WithException {
        ExecutionInterface entity;
        Exception exception;
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

        resultStream = kafkaStreamService.of(this.getClass(), this.topology(), properties);
        resultStream.start();

        applicationContext.registerSingleton(new KafkaTemplateExecutor(
            resultStream.store("template", QueryableStoreTypes.keyValueStore())
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
