package org.kestra.runner.kafka;

import io.micronaut.context.ApplicationContext;
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
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.*;
import org.kestra.core.utils.Either;
import org.kestra.runner.kafka.serializers.JsonSerde;
import org.kestra.runner.kafka.services.KafkaAdminService;
import org.kestra.runner.kafka.services.KafkaStreamService;
import org.kestra.runner.kafka.streams.DeduplicationPurgeTransformer;
import org.kestra.runner.kafka.streams.DeduplicationTransformer;

import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;

@KafkaQueueEnabled
@Prototype
@Slf4j
public class KafkaExecutor extends AbstractExecutor {
    private static final String WORKERTASK_DEDUPLICATION_STATE_STORE_NAME = "workertask_deduplication";
    private static final String TOPIC_EXECUTOR = "executor";

    KafkaStreamService kafkaStreamService;
    KafkaAdminService kafkaAdminService;
    FlowRepositoryInterface flowRepository;

    @Inject
    public KafkaExecutor(
        ApplicationContext applicationContext,
        FlowRepositoryInterface flowRepository,
        KafkaStreamService kafkaStreamService,
        KafkaAdminService kafkaAdminService,
        MetricRegistry metricRegistry
    ) {
        super(applicationContext, metricRegistry);
        this.flowRepository = flowRepository;
        this.kafkaStreamService = kafkaStreamService;
        this.kafkaAdminService = kafkaAdminService;
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

        KTable<String, Execution> executionKTable = this.executionKTable(builder);

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
        KStream<String, Execution> executionKStream = this.joinWorkerResult(builder, executionKTable);

        // handle state on execution
        KStream<String, ExecutionWithFlow> stream = this.withFlow(executionKStream);
        KStream<String, TaskRunExecutionWithFlow> streamTaskRuns = this.withTaskRun(stream);

        this.handleEnd(stream);
        this.handleListeners(stream);
        this.handleChild(streamTaskRuns);
        this.handleChildNext(streamTaskRuns);
        this.handleWorkerTask(streamTaskRuns);
        this.handleFlowNext(stream);
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
        KStream<String, HasJoin> result = executionKTable
            .leftJoin(
                this.workerTaskResultKTable(builder),
                (execution, workerTaskResultState) -> {
                    if (workerTaskResultState == null) {
                        return new HasJoin(execution, false);
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

                            try {
                                return new HasJoin(execution.withTaskRun(workerTaskResult.getTaskRun()), true);
                            } catch (Exception e) {
                                return new HasJoin(execution.failedExecutionFromExecutor(e), false);
                            }
                        })
                        .orElseGet(() -> new HasJoin(execution, false));
                },
                Named.as("join-leftJoin")
            )
            .toStream(Named.as("join-toStream"));

        return toExecutionJoin(result);
    }

    private KStream<String, ExecutionWithFlow> withFlow(KStream<String, Execution> executionKStream) {
        return executionKStream
            .mapValues(
                (readOnlyKey, execution) -> {
                    Flow flow = this.flowRepository.findByExecution(execution);

                    return new ExecutionWithFlow(flow, execution);
                },
                Named.as("withFlow-map")
            );
    }

    private KStream<String, TaskRunExecutionWithFlow> withTaskRun(KStream<String, ExecutionWithFlow> stream) {
        return stream
            .flatMapValues(
                (readOnlyKey, value) -> {
                    if (value.getExecution().getTaskRunList() == null) {
                        return new ArrayList<>();
                    }

                    return value
                        .getExecution()
                        .getTaskRunList()
                        .stream()
                        .map(taskRun -> new TaskRunExecutionWithFlow(value.getFlow(), value.getExecution(), taskRun))
                        .collect(Collectors.toList());
                },
                Named.as("withTaskRun-map")
            );
    }

    private void handleEnd(KStream<String, ExecutionWithFlow> stream) {
        KStream<String, Execution> result = stream
            .filter(
                (key, value) -> !value.getExecution().getState().isTerninated(),
                Named.as("handleEnd-terminated-filter")
            )
            .mapValues(
                (readOnlyKey, value) -> {
                    List<ResolvedTask> currentTasks = value.getExecution()
                        .findTaskDependingFlowState(
                            ResolvedTask.of(value.getFlow().getTasks()),
                            ResolvedTask.of(value.getFlow().getErrors())
                        );

                    if (value.getExecution().isTerminated(currentTasks)) {
                        return this.onEnd(value.getFlow(), value.getExecution());
                    }

                    return null;
                },
                Named.as("handleEnd-map")
            );

        this.toExecution(result, "handleEnd");
    }

    private void purgeExecutor(KStream<String, ExecutionWithFlow> stream) {
        KStream<String, ExecutionWithFlow> terminated = stream
            .filter(
                (key, value) -> value.getExecution().isTerminatedWithListeners(value.getFlow()),
                Named.as("purgeExecutor-terminated-filter")
            );

        // clean up executor
        terminated
            .mapValues(
                (readOnlyKey, value) -> (Execution) null,
                Named.as("purgeExecutor-toNull-map")
            )
            .to(
                kafkaAdminService.getTopicName(TOPIC_EXECUTOR),
                Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
            );

        // clean up workerTaskResult
        terminated
            .flatMapValues(
                (readOnlyKey, value) -> value.getExecution().getTaskRunList(),
                Named.as("workerTaskResultKTable-toTaskRun-flatmap")
            )
            .map(
                (readOnlyKey, value) -> new KeyValue<>(
                    value.getId(),
                    (WorkerTaskResult) null
                ),
                Named.as("workerTaskResultKTable-toNull-map")
            )
            .to(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class))
            );

        // clean up deduplication state
        terminated
            .flatMapValues((readOnlyKey, value) -> value.getExecution().getTaskRunList())
            .transformValues(
                () -> new DeduplicationPurgeTransformer<>(
                    WORKERTASK_DEDUPLICATION_STATE_STORE_NAME,
                    (key, value) -> value.getExecutionId() + "-" + value.getId()
                ),
                Named.as("workerTaskResultKTable-deduplicationPurge-transform"),
                WORKERTASK_DEDUPLICATION_STATE_STORE_NAME
            );
    }

    private void handleListeners(KStream<String, ExecutionWithFlow> stream) {
        KStream<String, Execution> result = stream
            .filter(
                (key, value) -> value.getExecution().getState().isTerninated(),
                Named.as("handleListeners-terminated-filter")
            )
            .mapValues(
                (readOnlyKey, value) -> {
                    List<ResolvedTask> currentTasks = value.getExecution().findValidListeners(value.getFlow());

                    List<TaskRun> next = FlowableUtils.resolveSequentialNexts(
                        value.getExecution(),
                        currentTasks,
                        new ArrayList<>()
                    );

                    if (next.size() > 0) {
                        return this.onNexts(value.getFlow(), value.getExecution(), next);
                    }

                    return null;
                },
                Named.as("handleListeners-map")
            )
            .filter((key, value) -> value != null, Named.as("handleListeners-null-filter"));

        this.toExecution(result, "handleListeners");
    }


    private void handleChild(KStream<String, TaskRunExecutionWithFlow> stream) {
        KStream<String, Either<WithException, WorkerTaskResult>> childWorkerTaskResult = stream
            .filter(
                (key, value) -> value.getTaskRun().getState().getCurrent() == State.Type.RUNNING,
                Named.as("handleChild-running-filter")
            )
            .mapValues(
                (readOnlyKey, value) -> {
                    try {
                        return Either.right(this.childWorkerTaskResult(
                            value.getFlow(),
                            value.getExecution(),
                            value.getTaskRun()
                        ).orElse(null));
                    } catch (Exception e) {
                        return Either.left(new WithException(value, e));
                    }
                },
                Named.as("handleChild-map")
            );

        branchException(childWorkerTaskResult, "handleChild")
            .filter((key, value) -> value != null, Named.as("handleChild-null-filter"))
            .selectKey(
                (key, value) -> value.getTaskRun().getId(),
                Named.as("handleChild-selectKey")
            )
            .to(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class))
            );

    }

    private void handleChildNext(KStream<String, TaskRunExecutionWithFlow> stream) {
        KStream<String, Either<WithException, Execution>> streamEither = stream
            .filter(
                (key, value) -> value.getTaskRun().getState().getCurrent() == State.Type.RUNNING,
                Named.as("handleChildNext-running-filter")
            )
            .mapValues(
                (readOnlyKey, value) -> {
                    try {
                        return Either.right(
                            this.childNexts(value.getFlow(), value.getExecution(), value.getTaskRun())
                                .orElse(null)
                        );
                    } catch (Exception e) {
                        return Either.left(new WithException(value, e));
                    }
                },
                Named.as("handleChildNext-map")
            );

        KStream<String, Execution> result = branchException(streamEither, "handleChildNext");

        this.toExecution(result, "handleChildNext");
    }

    private void handleWorkerTask(KStream<String, TaskRunExecutionWithFlow> stream) {
        KStream<String, Either<WithException, WorkerTask>> streamEither = stream
            .filter(
                (key, value) -> value.getTaskRun().getState().getCurrent() == State.Type.CREATED,
                Named.as("handleWorkerTask-created-filter")
            )
            .mapValues(
                (readOnlyKey, taskRunExecutionWithFlow) -> {
                    try {
                        ResolvedTask resolvedTask = taskRunExecutionWithFlow.getFlow().findTaskByTaskRun(
                            taskRunExecutionWithFlow.getTaskRun(),
                            new RunContext(
                                this.applicationContext,
                                taskRunExecutionWithFlow.getFlow(),
                                taskRunExecutionWithFlow.getExecution()
                            )
                        );

                        return Either.right(
                            WorkerTask.builder()
                                .runContext(new RunContext(
                                        this.applicationContext,
                                        taskRunExecutionWithFlow.getFlow(),
                                        resolvedTask,
                                        taskRunExecutionWithFlow.getExecution(),
                                        taskRunExecutionWithFlow.getTaskRun()
                                    )
                                )
                                .taskRun(taskRunExecutionWithFlow.getTaskRun())
                                .task(resolvedTask.getTask())
                                .build()
                        );
                    } catch (Exception e) {
                        return Either.left(new WithException(taskRunExecutionWithFlow, e));
                    }
                },
                Named.as("handleWorkerTask-map")
            );

        branchException(streamEither, "handleWorkerTask")
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
            .to(
                kafkaAdminService.getTopicName(WorkerTask.class),
                Produced.with(Serdes.String(), JsonSerde.of(WorkerTask.class))
            );
    }

    private void handleFlowNext(KStream<String, ExecutionWithFlow> stream) {
        KStream<String, Execution> result = stream
            .mapValues(
                (readOnlyKey, value) -> {
                    List<TaskRun> next = FlowableUtils.resolveSequentialNexts(
                        value.getExecution(),
                        ResolvedTask.of(value.getFlow().getTasks()),
                        ResolvedTask.of(value.getFlow().getErrors())
                    );

                    if (next.size() > 0) {
                        return this.onNexts(value.getFlow(), value.getExecution(), next);
                    }

                    return null;
                },
                Named.as("handleFlowNext-map")
            );

        this.toExecution(result, "handleFlowNext");
    }

    private <T> KStream<String, T> branchException(KStream<String, Either<WithException, T>> stream, String methodName) {
        methodName = methodName + "-branchException";

        KStream<String, Execution> result = stream
            .filter((key, value) -> value.isLeft(), Named.as(methodName + "-isLeft-filter"))
            .mapValues(Either::getLeft, Named.as(methodName + "-isLeft-map"))
            .mapValues(
                e -> e.getEntity().getExecution().failedExecutionFromExecutor(e.getException()),
                Named.as(methodName + "-isLeft-failedExecutionFromExecutor-map")
            );

        this.toExecution(result, methodName + "-isLeft");

        return stream
            .filter((key, value) -> value.isRight(), Named.as(methodName + "-isRight-filter"))
            .mapValues(Either::getRight, Named.as(methodName + "-isRight-map"));
    }


    private KStream<String, Execution> toExecutionJoin(KStream<String, HasJoin> stream) {
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
    public static class HasJoin implements ExecutionInterface {
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
    public static class TaskRunExecutionWithFlow implements ExecutionInterface {
        Flow flow;
        Execution execution;
        TaskRun taskRun;
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
        kafkaAdminService.createIfNotExist(TOPIC_EXECUTOR);

        KafkaStreamService.Stream resultStream = kafkaStreamService.of(this.getClass(), this.topology());
        resultStream.start();
    }
}
