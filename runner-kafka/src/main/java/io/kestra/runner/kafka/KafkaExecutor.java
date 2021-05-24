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
import io.kestra.core.utils.Either;
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
import org.apache.kafka.common.header.Header;
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
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@KafkaQueueEnabled
@Singleton
@Slf4j
public class KafkaExecutor extends AbstractExecutor implements Closeable {
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
            Consumed.with(Serdes.String(), JsonSerde.of(WorkerInstance.class)).withName("GlobalStore.ExecutorWorkerInstace"),
            () -> new GlobalStateProcessor<>(WORKERINSTANCE_STATE_STORE_NAME)
        );

        // declare ktable & kstream
        KStream<String, WorkerTaskResult> workerTaskResultKStream = this.workerTaskResultKStream(builder);
        KTable<String, KafkaExecutor.Executor> executorKTable = this.executorKTable(builder);
        KTable<String, WorkerTaskResultState> workerTaskResultKTable = this.workerTaskResultKTable(workerTaskResultKStream);
        GlobalKTable<String, Flow> flowKTable = kafkaStreamSourceService.flowGlobalKTable(builder);
        GlobalKTable<String, WorkerTaskRunning> workerTaskRunningKTable = this.workerTaskRunningKStream(builder);
        KStream<String, WorkerInstance> workerInstanceKStream = this.workerInstanceKStream(builder);
        KStream<String, ExecutorFlowTrigger> flowWithTriggerStream = this.flowWithTriggerStream(builder);
        this.templateKTable(builder);

        // logs
        KafkaStreamSourceService.logIfEnabled(
            executorKTable.toStream(Named.as("ExecutionIn.toStream")),
            (key, value) -> log.debug(
                "Execution << IN [key='{}', crc='{}', offset='{}']\n{}",
                value.getExecution().getId(),
                value.getExecution().toCrc32State(),
                value.getOffset(),
                value.getExecution().toStringState()
            ),
            "ExecutionIn"
        );

        // join with killed & worker result
        KTable<String, Executor> executionWithKilled = this.joinExecutionKilled(builder, executorKTable);
        KStream<String, Executor> executionKStream = this.joinWorkerResult(workerTaskResultKTable, executionWithKilled);

        // handle state on execution
        KStream<String, ExecutorWithFlow> stream = kafkaStreamSourceService.executorWithFlow(flowKTable, executionKStream);

        stream = this.handleMain(stream);
        stream = this.handleNexts(stream);

        stream = this.handleWorkerTask(stream);
        stream = this.handleWorkerTaskResult(stream);

        // save execution
        this.toExecution(stream);

        // trigger
        this.handleExecutorFlowTriggerTopic(stream);
        this.handleFlowTrigger(flowWithTriggerStream);

        this.purgeExecutor(stream);

        // handle worker
        this.purgeWorkerRunning(workerTaskResultKStream);
        this.detectNewWorker(workerInstanceKStream, workerTaskRunningKTable);

        return builder;
    }

    private void executionToExecutor(StreamsBuilder builder) {
        builder
            .stream(
                kafkaAdminService.getTopicName(Execution.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("ExecutionToExecutor.fromExecution")
            )
            .filter((key, value) -> value.getTaskRunList() == null ||
                value.getTaskRunList().size() == 0 ||
                value.isJustRestarted(),
                Named.as("ExecutionToExecutor.filter")
            )
            .to(
                kafkaAdminService.getTopicName(KafkaStreamSourceService.TOPIC_EXECUTOR),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("ExecutionToExecutor.toExecutor")
            );
    }

    public KTable<String, KafkaExecutor.Executor> executorKTable(StreamsBuilder builder) {
        return builder
            .table(
                kafkaAdminService.getTopicName(KafkaStreamSourceService.TOPIC_EXECUTOR),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("KTable.Executor"),
                Materialized.<String, Execution, KeyValueStore<Bytes, byte[]>>as("execution")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Execution.class))
            )
            .transformValues(() -> new ValueTransformerWithKey<String, Execution, KafkaExecutor.Executor>() {
                ProcessorContext context;
                @Override
                public void init(ProcessorContext context) {
                    this.context = context;
                }

                @Override
                public KafkaExecutor.Executor transform(String readOnlyKey, Execution value) {
                    if (value == null) {
                        return null;
                    }

                    this.context.headers().remove("from");
                    this.context.headers().remove("offset");

                    return new KafkaExecutor.Executor(
                        value,
                        this.context.offset()
                    );
                }

                @Override
                public void close() {

                }
            });
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

    private KTable<String, ExecutionKilled> executionKilledKTable(StreamsBuilder builder) {
        return builder
            .table(
                kafkaAdminService.getTopicName(ExecutionKilled.class),
                Consumed.with(Serdes.String(), JsonSerde.of(ExecutionKilled.class)).withName("KTable.ExecutionKilled"),
                Materialized.<String, ExecutionKilled, KeyValueStore<Bytes, byte[]>>as("execution_killed")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(ExecutionKilled.class))
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

    private KTable<String, Executor> joinExecutionKilled(StreamsBuilder builder, KTable<String, KafkaExecutor.Executor> executionKTable) {
        return executionKTable
            .leftJoin(
                this.executionKilledKTable(builder),
                (executor, executionKilled) -> {
                    if (executionKilled != null &&
                        executor.getExecution().getState().getCurrent() != State.Type.KILLING &&
                        !executor.getExecution().getState().isTerninated()
                    ) {
                        Execution newExecution = executor.getExecution().withState(State.Type.KILLING);

                        if (log.isDebugEnabled()) {
                            log.debug("Killed << IN\n{}", newExecution.toStringState());
                        }

                        return executor.withExecution(newExecution, "joinExecutionKilled");
                    }

                    return executor;
                },
                Named.as("JoinExecutionKilled.leftJoin")
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
                        log.debug("Aggregate << IN : {}", newValue.getTaskRun().toStringState());
                    }

                    aggregate
                        .getResults()
                        .compute(
                            newValue.getTaskRun().getId(),
                            (s, workerTaskResult) -> newValue
                        );

                    return aggregate;
                },
                Named.as("WorkerTaskResultKTable.aggregate"),
                Materialized.<String, WorkerTaskResultState, KeyValueStore<Bytes, byte[]>>as("workertaskresult")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(WorkerTaskResultState.class))
            );
    }

    private KStream<String, Executor> joinWorkerResult(KTable<String, WorkerTaskResultState> workerTaskResultKTable, KTable<String, Executor> executionKTable) {
        return executionKTable
            .leftJoin(
                workerTaskResultKTable,
                (Executor executor, WorkerTaskResultState workerTaskResultState) -> {
                    if (workerTaskResultState == null) {
                        return executor;
                    }

                    // all joinables results
                    Executor finalExecutor = executor;
                    List<WorkerTaskResult> workerTaskResultJoinables = workerTaskResultState
                        .getResults()
                        .values()
                        .stream()
                        .filter(workerTaskResult -> finalExecutor.getExecution().hasTaskRunJoinable(workerTaskResult.getTaskRun()))
                        .collect(Collectors.toList());

                    if (workerTaskResultJoinables.size() == 0) {
                        return executor;
                    }

                    for (WorkerTaskResult workerTaskResult : workerTaskResultJoinables) {
                        try {
                            Execution newExecution = executor.getExecution().withTaskRun(workerTaskResult.getTaskRun());

                            if (log.isDebugEnabled()) {
                                log.debug(
                                    "WorkerTaskResult << IN [key='{}', crc='{}', offset='{}']\n{}",
                                    newExecution.getId(),
                                    newExecution.toCrc32State(),
                                    executor.getOffset(),
                                    newExecution.toStringState()
                                );
                            }

                            executor = executor.withExecution(newExecution, "joinWorkerResult");

                        } catch (Exception e) {
                            return executor.withException(e, "joinWorkerResult");
                        }

                        // send metrics
                        metricRegistry
                            .counter(
                                MetricRegistry.KESTRA_EXECUTOR_TASKRUN_ENDED_COUNT,
                                metricRegistry.tags(workerTaskResult)
                            )
                            .increment();

                        metricRegistry
                            .timer(
                                MetricRegistry.KESTRA_EXECUTOR_TASKRUN_ENDED_DURATION,
                                metricRegistry.tags(workerTaskResult)
                            )
                            .record(workerTaskResult.getTaskRun().getState().getDuration());
                    }

                    return executor;
                },
                Named.as("JoinWorkerResult.leftJoin")
            )
            .toStream(Named.as("JoinWorkerResult.toStream"));
    }

    private KStream<String, ExecutorWithFlow> handleMain(KStream<String, ExecutorWithFlow> stream) {
        return stream
            .mapValues(
                (readOnlyKey, executorWithFlow) -> {
//                    if (executorWithFlow.hasChanged()) {
//                        return executorWithFlow;
//                    }

                    Optional<Execution> main = this.doMain(executorWithFlow.getExecution(), executorWithFlow.getFlow());

                    return main
                        .map(r -> executorWithFlow.withExecution(r, "handleMain"))
                        .orElse(executorWithFlow);
                },
                Named.as("HandleMain.map")
            );
    }

    private void purgeExecutor(KStream<String, ExecutorWithFlow> stream) {
        KStream<String, ExecutorWithFlow> terminatedWithKilled = stream
            .filter(
                (key, value) -> conditionService.isTerminatedWithListeners(value.getFlow(), value.getExecution()),
                Named.as("PurgeExecutor.filterTerminated")
            );

        // we don't purge killed execution in order to have feedback about child running tasks
        // this can be killed lately (after the executor kill the execution), but we want to keep
        // feedback about the actual state (killed or not)
        // @TODO: this can lead to infinite state store for most executor topic
        KStream<String, ExecutorWithFlow> terminated = terminatedWithKilled.filter(
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

    private void handleExecutorFlowTriggerTopic(KStream<String, ExecutorWithFlow> stream) {
        stream
            .filter(
                (key, value) -> conditionService.isTerminatedWithListeners(value.getFlow(), value.getExecution()),
                Named.as("HandleExecutorFlowTriggerTopic.filterTerminated")
            )
            .transformValues(
                () -> new DeduplicationTransformer<>(
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

    private KStream<String, ExecutorWithFlow> handleNexts(KStream<String, ExecutorWithFlow> stream) {
        return stream
            .transformValues(
                () -> new ExecutionNextsDeduplicationTransformer(
                    NEXTS_DEDUPLICATION_STATE_STORE_NAME,
                    this
                ),
                Named.as("HandleNexts.transformToNextWithDeduplication"),
                NEXTS_DEDUPLICATION_STATE_STORE_NAME
            );
    }

    private KStream<String, ExecutorWithFlow> handleWorkerTask(KStream<String, ExecutorWithFlow> stream) {
        KStream<String, Either<WithException, Pair<ExecutorWithFlow, List<WorkerTask>>>> streamEither = stream
            .mapValues(
                (readOnlyKey, executorWithFlow) -> {
                    try {
                        return Either.right(
                            Pair.of(
                                executorWithFlow,
                                this.doWorkerTask(executorWithFlow.getExecution(), executorWithFlow.getFlow()).orElse(null)
                            )

                        );
                    } catch (Exception e) {
                        return Either.left(new WithException(executorWithFlow, e));
                    }
                },
                Named.as("HandleWorkerTask.mapToWorkerTaskList")
            );

        return branchException(
            streamEither,
            successStream -> {
                KStream<String, WorkerTask> dedupWorkerTask = successStream
                    .flatMapValues(
                        (readOnlyKey, value) -> value,
                        Named.as("HandleWorkerTask.flatMapToWorkerTask")
                    )
                    .transformValues(
                        () -> new DeduplicationTransformer<>(
                            WORKERTASK_DEDUPLICATION_STATE_STORE_NAME,
                            (key, value) -> value.getTaskRun().getExecutionId() + "-" + value.getTaskRun().getId(),
                            (key, value) -> value.getTaskRun().getState().getCurrent().name()
                        ),
                        Named.as("HandleWorkerTask.deduplication"),
                        WORKERTASK_DEDUPLICATION_STATE_STORE_NAME
                    )
                    .filter((key, value) -> value != null, Named.as("HandleWorkerTask.notNullFilter"));

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
                    (key, value) -> log.debug(
                        "WorkerTaskResult >> OUT : {}",
                        value.getTaskRun().toStringState()
                    ),
                    "HandleWorkerTaskFlowable"
                );

                workerTaskResultKStream
                    .to(
                        kafkaAdminService.getTopicName(WorkerTaskResult.class),
                        Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class)).withName("HandleWorkerTaskFlowable.toWorkerTaskResult")
                    );

                KStream<String, WorkerTask> resultNotFlowable = dedupWorkerTask
                    .filter((key, value) -> !value.getTask().isFlowable(), Named.as("HandleWorkerTaskNotFlowable.filterIsNotFlowable"))
                    .map((key, value) -> new KeyValue<>(queueService.key(value), value), Named.as("HandleWorkerTaskNotFlowable.mapWithKey"))
                    .selectKey(
                        (key, value) -> queueService.key(value),
                        Named.as("HandleWorkerTaskNotFlowable.selectKey")
                    );

                KStream<String, WorkerTask> workerTaskKStream = KafkaStreamSourceService.logIfEnabled(
                    resultNotFlowable,
                    (key, value) -> log.debug(
                        "WorkerTaskResult >> OUT : {}",
                        value.getTaskRun().toStringState()
                    ),
                    "HandleWorkerTaskNotFlowable"
                );

                workerTaskKStream
                    .to(
                        kafkaAdminService.getTopicName(WorkerTask.class),
                        Produced.with(Serdes.String(), JsonSerde.of(WorkerTask.class)).withName("HandleWorkerTaskNotFlowable.toWorkerTask")
                    );
            },
            "HandleWorkerTask"
        );
    }

    private KStream<String, ExecutorWithFlow> handleWorkerTaskResult(KStream<String, ExecutorWithFlow> stream) {
        KStream<String, Either<WithException, Pair<ExecutorWithFlow, List<WorkerTaskResult>>>> streamEither = stream
            .mapValues(
                (readOnlyKey, value) -> {
                    try {
                        return Either.right(
                            Pair.of(
                                value,
                                this.doWorkerTaskResult(value.getExecution(), value.getFlow()).orElse(null)
                            )
                        );
                    } catch (Exception e) {
                        return Either.left(new WithException(value, e));
                    }
                },
                Named.as("HandleWorkerTaskResult.map")
            );

        return branchException(
            streamEither,
            successStream -> {
                KStream<String, WorkerTaskResult> WorkerTaskResult = successStream
                    .flatMapValues((readOnlyKey, value) -> value, Named.as("HandleWorkerTaskResult.flapMap"))
                    .transformValues(
                        () -> new DeduplicationTransformer<>(
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
                    WorkerTaskResult,
                    (key, value) -> log.debug(
                        "WorkerTaskResult >> OUT : {}",
                        value.getTaskRun().toStringState()
                    ),
                    "HandleWorkerTaskResult"
                )
                    .to(
                        kafkaAdminService.getTopicName(WorkerTaskResult.class),
                        Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class))
                    );
            },
            "HandleWorkerTaskResult"
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
                "WorkerTask resend >> OUT : {}",
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

    private <T> KStream<String, ExecutorWithFlow> branchException(
        KStream<String, Either<WithException, Pair<ExecutorWithFlow, T>>> stream,
        Consumer<KStream<String, T>> consumer,
        String methodName
    ) {
        String finalMethodName = methodName + "BranchException";

        consumer.accept(
            stream
                .filter((key, value) -> value != null, Named.as(methodName + "Right.notNullFilter"))
                .filter((key, value) -> value.isRight(), Named.as(methodName + "Right.isRight"))
                .mapValues((readOnlyKey, value) -> value.getRight().getRight(), Named.as(methodName + "Right.mapToRight"))
                .filter((key, value) -> value != null, Named.as(methodName + "Right.notNullRightFilter"))
        );

        return stream
            .mapValues(
                (readOnlyKey, value) -> {
                    if (value.isRight()) {
                        return value.getRight().getLeft();
                    }

                    return value
                        .getLeft()
                        .getExecutorWithFlow()
                        .withException(
                            value.getLeft().getException(),
                            finalMethodName
                        );
                },
                Named.as(finalMethodName + "Left.isLeft")
            );
    }

    private void toExecution(KStream<String, ExecutorWithFlow> stream) {
        KStream<String, ExecutorWithFlow> streamFrom = stream
            .transformValues(() -> new ValueTransformer<ExecutorWithFlow, ExecutorWithFlow>() {
                ProcessorContext context;

                @Override
                public void init(ProcessorContext context) {
                    this.context = context;
                }

                @Override
                public ExecutorWithFlow transform(ExecutorWithFlow value) {
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
            })
            .filter((key, value) -> value.getFrom().size() > 0, Named.as("ToExecution.haveFrom"));


        streamFrom = KafkaStreamSourceService.logIfEnabled(
            streamFrom,
            (key, value) -> log.debug(
                "Execution >> OUT [key='{}', crc='{}', from='{}', offset='{}']\n{}",
                value.getExecution().getId(),
                value.getExecution().toCrc32State(),
                value.getFrom(),
                value.getOffset(),
                value.getExecution().toStringState()
            ),
            "ToExecution"
        );

        // send execution
        KStream<String, Execution> executionKStream = streamFrom
            .filter((key, value) -> value.getException() == null, Named.as("ToExecutionExecution.notException"))
            .mapValues((readOnlyKey, value) -> value.getExecution(), Named.as("ToExecutionExecution.mapToExecution"))
            .filter((key, value) -> value != null, Named.as("ToExecutionExecution.notNullExecution"));

        executionKStream
            .to(
                kafkaAdminService.getTopicName(Execution.class),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("ToExecutionExecution.toExecution")
            );

        executionKStream
            .to(
                kafkaAdminService.getTopicName(KafkaStreamSourceService.TOPIC_EXECUTOR),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("ToExecutionExecution.toExecutor")
            );

        // send exception
        KStream<String, Execution.FailedExecutionWithLog> failedStream = streamFrom
            .filter((key, value) -> value.getException() != null, Named.as("ToExecutionException.isException"))
            .mapValues(
                e -> e.getExecution().failedExecutionFromExecutor(e.getException()),
                Named.as("ToExecutionException.mapToFailedExecutionWithLog")
            );

        failedStream
            .flatMapValues(Execution.FailedExecutionWithLog::getLogs, Named.as("ToExecutionException.flatmapLogs"))
            .to(
                kafkaAdminService.getTopicName(LogEntry.class),
                Produced.with(Serdes.String(), JsonSerde.of(LogEntry.class)).withName("ToExecutionException.toLogEntry")
            );

        KStream<String, Execution> failedExecutionResult = failedStream
            .mapValues(Execution.FailedExecutionWithLog::getExecution, Named.as("ToExecutionException.mapToExecution"));

        failedExecutionResult
            .to(
                kafkaAdminService.getTopicName(Execution.class),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("ToExecutionException.toExecution")
            );

        failedExecutionResult
            .to(
                kafkaAdminService.getTopicName(KafkaStreamSourceService.TOPIC_EXECUTOR),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class)).withName("ToExecutionException.toExecutor")
            );
    }

    @Getter
    @AllArgsConstructor
    public static class Executor {
        Execution execution;
        Exception exception;
        List<String> from = new ArrayList<>();
        Long offset;

        public Executor(Execution execution, Long offset) {
            this.execution = execution;
            this.offset = offset;
        }

        public boolean hasChanged() {
            return this.from.size() > 0 || this.exception != null;
        }

        public Executor withExecution(Execution execution, String from) {
            this.execution = execution;
            this.from.add(from);

            return this;
        }

        public Executor withException(Exception exception, String from) {
            this.exception = exception;
            this.from.add(from);

            return this;
        }
    }

    @Getter
    public static class ExecutorWithFlow extends Executor {
        Flow flow;

        public ExecutorWithFlow(Executor executor, Flow flow) {
            super(executor.getExecution(), executor.getException(), executor.getFrom(), executor.getOffset());
            this.flow = flow;
        }

        public ExecutorWithFlow withExecution(Execution execution, String from) {
            this.execution = execution;
            this.from.add(from);

            return this;
        }

        public ExecutorWithFlow withException(Exception exception, String from) {
            this.exception = exception;
            this.from.add(from);

            return this;
        }
    }

    @AllArgsConstructor
    @Getter
    public static class ExecutorNexts  {
        ExecutorWithFlow executor;
        List<TaskRun> nexts;
    }

    @AllArgsConstructor
    @Getter
    public static class WithException {
        ExecutorWithFlow executorWithFlow;
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
