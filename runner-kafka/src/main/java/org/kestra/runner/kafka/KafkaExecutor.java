package org.kestra.runner.kafka;

import io.micronaut.context.annotation.Prototype;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.ExecutionKilled;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.runners.AbstractExecutor;
import org.kestra.core.runners.RunContextFactory;
import org.kestra.core.runners.WorkerTask;
import org.kestra.core.runners.WorkerTaskResult;
import org.kestra.core.services.FlowService;
import org.kestra.core.utils.Either;
import org.kestra.runner.kafka.serializers.JsonSerde;
import org.kestra.runner.kafka.services.KafkaAdminService;
import org.kestra.runner.kafka.services.KafkaStreamService;
import org.kestra.runner.kafka.streams.DeduplicationPurgeTransformer;
import org.kestra.runner.kafka.streams.DeduplicationTransformer;
import org.kestra.runner.kafka.streams.ExecutionNextsDeduplicationTransformer;
import org.kestra.runner.kafka.streams.FlowTriggerTransformer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;

@KafkaQueueEnabled
@Prototype
@Slf4j
public class KafkaExecutor extends AbstractExecutor {
    private static final String WORKERTASK_DEDUPLICATION_STATE_STORE_NAME = "workertask_deduplication";
    private static final String TRIGGER_DEDUPLICATION_STATE_STORE_NAME = "trigger_deduplication";
    private static final String NEXTS_DEDUPLICATION_STATE_STORE_NAME = "next_deduplication";
    private static final String TOPIC_EXECUTOR = "executor";

    KafkaStreamService kafkaStreamService;
    KafkaAdminService kafkaAdminService;
    QueueInterface<LogEntry> logQueue;
    FlowService flowService;

    @Inject
    public KafkaExecutor(
        RunContextFactory runContextFactory,
        KafkaStreamService kafkaStreamService,
        KafkaAdminService kafkaAdminService,
        @javax.inject.Named(QueueFactoryInterface.WORKERTASKLOG_NAMED) QueueInterface<LogEntry> logQueue,
        MetricRegistry metricRegistry,
        FlowService flowService
    ) {
        super(runContextFactory, metricRegistry);

        this.kafkaStreamService = kafkaStreamService;
        this.kafkaAdminService = kafkaAdminService;
        this.logQueue = logQueue;
        this.flowService = flowService;
    }

    public Topology topology() {
        StreamsBuilder builder = new StreamsBuilder();

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

        KTable<String, Execution> executionKTable = this.executionKTable(builder);

        KTable<String, Execution> executionNotKilledKTable = this.joinExecutionKilled(builder, executionKTable);

        // logs
        logIfEnabled(
            executionKTable.toStream(Named.as("execution-toStream")),
            (key, value) -> log.debug(
                "Execution in '{}' with checksum '{}': {}",
                value.getId(),
                value.toCrc32State(),
                value.toStringState()
            ),
            "execution-in"
        );

        // join with worker result
        KStream<String, Execution> executionKStream = this.joinWorkerResult(builder, executionNotKilledKTable);

        GlobalKTable<String, Flow> flowKTable = this.flowKTable(builder);

        // handle state on execution
        KStream<String, ExecutionWithFlow> stream = this.withFlow(flowKTable, executionKStream);

        this.handleMain(stream);
        this.handleNexts(stream);

        this.handleWorkerTask(stream);
        this.handleWorkerTaskResult(stream);

        this.handleFlowTrigger(stream);

        this.purgeExecutor(stream);


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
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
            );
    }

    private GlobalKTable<String, Flow> flowKTable(StreamsBuilder builder) {
        return builder
            .globalTable(
                kafkaAdminService.getTopicName(Flow.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Flow.class)),
                Materialized.<String, Flow, KeyValueStore<Bytes, byte[]>>as("flow")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Flow.class))
            );
    }
    
    private KTable<String, Execution> executionKTable(StreamsBuilder builder) {
        return builder
            .table(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR),
                Consumed.with(Serdes.String(), JsonSerde.of(Execution.class)),
                Materialized.<String, Execution, KeyValueStore<Bytes, byte[]>>as("execution")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Execution.class))
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

    private KTable<String, WorkerTaskResultState> workerTaskResultKTable(StreamsBuilder builder) {
        return builder
            .stream(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Consumed.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class))
            )
            .filter((key, value) -> value != null, Named.as("workerTaskResultKTable-null-filter"))
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

    private KStream<String, Execution> joinWorkerResult(StreamsBuilder builder, KTable<String, Execution> executionKTable) {
        KStream<String, Either<WithException, HasWorkerResultJoin>> eitherKStream = executionKTable
            .leftJoin(
                this.workerTaskResultKTable(builder),
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

    private KStream<String, ExecutionWithFlow> withFlow(GlobalKTable<String, Flow> flowGlobalKTable, KStream<String, Execution> executionKStream) {
        return executionKStream
            .join(
                flowGlobalKTable,
                (key, value) -> Flow.uid(value),
                (execution, flow) -> new ExecutionWithFlow(flow, execution),
                Named.as("withFlow-join")
            );
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
                (key, value) -> value.getExecution().isTerminatedWithListeners(value.getFlow()),
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
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR),
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

    private void handleFlowTrigger(KStream<String, ExecutionWithFlow> stream) {
        stream
            .filter(
                (key, value) -> value.getExecution().isTerminatedWithListeners(value.getFlow()),
                Named.as("handleFlowTrigger-terminated-filter")
            )
            .transformValues(
                () -> new DeduplicationTransformer<>(
                    TRIGGER_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> value.getExecution().getId(),
                    (key, value) -> value.getExecution().getId()
                ),
                Named.as("handleFlowTrigger-deduplication-transform"),
                TRIGGER_DEDUPLICATION_STATE_STORE_NAME
            )
            .filter((key, value) -> value != null, Named.as("handleFlowTrigger-dedupNull-filter"))
            .transformValues(
                () -> new FlowTriggerTransformer(flowService),
                Named.as("handleFlowTrigger-trigger-transform")
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

        KStream<String, WorkerTask> result = branchException(streamEither, "handleWorkerTask")
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
            .filter((key, value) -> value != null, Named.as("handleWorkerTask-null-filter"))
            .map((key, value) -> new KeyValue<>(value.getTaskRun().getId(), value))
            .selectKey(
                (key, value) -> value.getTaskRun().getId(),
                Named.as("handleWorkerTask-selectKey")
            );

        KStream<String, WorkerTask> workerTaskKStream = logIfEnabled(
            result,
            (key, value) -> log.debug(
                "WorkerTask out: {}",
                value.getTaskRun().toStringState()
            ),
            "handleWorkerTask-log"
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

        KStream<String, Execution> executionKStream = logIfEnabled(
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
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
            );
    }

    private static <T> KStream<String, T> logIfEnabled(KStream<String, T> stream, ForeachAction<String, T> action, String name) {
        if (log.isDebugEnabled()) {
            return stream
                .filter((key, value) -> value != null, Named.as(name + "-null-filter"))
                .peek(action, Named.as(name + "-peek"));
        } else {
            return stream;
        }
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

    @Override
    public void run() {
        kafkaAdminService.createIfNotExist(WorkerTaskResult.class);
        kafkaAdminService.createIfNotExist(Execution.class);
        kafkaAdminService.createIfNotExist(Flow.class);
        kafkaAdminService.createIfNotExist(TOPIC_EXECUTOR);
        kafkaAdminService.createIfNotExist(ExecutionKilled.class);

        KafkaStreamService.Stream resultStream = kafkaStreamService.of(this.getClass(), this.topology());
        resultStream.start();
    }
}
