package org.kestra.runner.kafka;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Prototype;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;

@KafkaQueueEnabled
@Prototype
public class KafkaExecutor extends AbstractExecutor {
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

    public static class ExecutionState extends AbstractExecutor {
        KafkaStreamService kafkaStreamService;
        KafkaAdminService kafkaAdminService;
        FlowRepositoryInterface flowRepository;

        public ExecutionState(
            ApplicationContext applicationContext,
            KafkaStreamService kafkaStreamService,
            KafkaAdminService kafkaAdminService,
            FlowRepositoryInterface flowRepository,
            MetricRegistry metricRegistry
        ) {
            super(applicationContext, metricRegistry);
            this.kafkaStreamService = kafkaStreamService;
            this.kafkaAdminService = kafkaAdminService;
            this.flowRepository = flowRepository;
        }

        private Topology topology() {
            kafkaAdminService.createIfNotExist(WorkerTaskResult.class);
            kafkaAdminService.createIfNotExist(Execution.class);

            StreamsBuilder builder = new StreamsBuilder();

            KStream<String, ExecutionWithFlow> stream = this.withFlow(builder);
            KStream<String, TaskRunExecutionWithFlow> streamTaskRuns = this.withTaskRun(stream);

            this.handleEnd(stream);
            this.handleListeners(stream);
            this.handlChild(streamTaskRuns);
            this.handlChildNext(streamTaskRuns);
            this.handleWorkerTask(streamTaskRuns);
            this.handleNext(stream);

            return builder.build();
        }

        private KStream<String, ExecutionWithFlow> withFlow(StreamsBuilder builder) {
            return builder
                .stream(
                    kafkaAdminService.getTopicName(Execution.class),
                    Consumed.with(Serdes.String(), JsonSerde.of(Execution.class))
                )
                .mapValues((readOnlyKey, execution) -> {
                    Flow flow = this.flowRepository.findByExecution(execution);

                    return new ExecutionWithFlow(flow, execution);
                });
        }

        private KStream<String, TaskRunExecutionWithFlow> withTaskRun(KStream<String, ExecutionWithFlow> stream) {
            return stream
                .flatMapValues((readOnlyKey, value) -> {
                    if (value.getExecution().getTaskRunList() == null) {
                        return new ArrayList<>();
                    }

                    return value
                        .getExecution()
                        .getTaskRunList()
                        .stream()
                        .map(taskRun -> new TaskRunExecutionWithFlow(value.getFlow(), value.getExecution(), taskRun))
                        .collect(Collectors.toList());
                });
        }

        private void handleEnd(KStream<String, ExecutionWithFlow> stream) {
            stream
                .filter((key, value) -> !value.getExecution().getState().isTerninated())
                .mapValues((readOnlyKey, value) -> {
                    List<ResolvedTask> currentTasks = value.getExecution()
                        .findTaskDependingFlowState(
                            ResolvedTask.of(value.getFlow().getTasks()),
                            ResolvedTask.of(value.getFlow().getErrors())
                        );

                    if (value.getExecution().isTerminated(currentTasks)) {
                        return this.onEnd(value.getFlow(), value.getExecution());
                    }

                    return null;
                })
                .filter((key, value) -> value != null)
                .to(
                    kafkaAdminService.getTopicName(Execution.class),
                    Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
                );
        }


        private void handleListeners(KStream<String, ExecutionWithFlow> stream) {
            stream
                .filter((key, value) -> value.getExecution().getState().isTerninated())
                .mapValues((readOnlyKey, value) -> {
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
                })
                .filter((key, value) -> value != null)

                .to(
                    kafkaAdminService.getTopicName(Execution.class),
                    Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
                );
        }

        private void handlChild(KStream<String, TaskRunExecutionWithFlow> stream) {
            KStream<String, Either<WithException, WorkerTaskResult>> childWorkerTaskResult = stream
                .filter((key, value) -> value.getTaskRun().getState().getCurrent() == State.Type.RUNNING)
                .mapValues((readOnlyKey, value) -> {
                    try {
                        return Either.right(this.childWorkerTaskResult(
                            value.getFlow(),
                            value.getExecution(),
                            value.getTaskRun()
                        ).orElse(null));
                    } catch (Exception e) {
                        return Either.left(new WithException(value, e));
                    }
                });

            branchException(childWorkerTaskResult)
                .filter((key, value) -> value != null)
                .to(
                    kafkaAdminService.getTopicName(WorkerTaskResult.class),
                    Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class))
                );

        }

        private void handlChildNext(KStream<String, TaskRunExecutionWithFlow> stream) {
            KStream<String, Either<WithException, Execution>> streamEither = stream
                .filter((key, value) -> value.getTaskRun().getState().getCurrent() == State.Type.RUNNING)
                .mapValues((readOnlyKey, value) -> {
                    try {
                        return Either.right(
                            this.childNexts(value.getFlow(), value.getExecution(), value.getTaskRun())
                                .orElse(null)
                        );
                    } catch (Exception e) {
                        return Either.left(new WithException(value, e));
                    }
                });

            branchException(streamEither)
                .filter((key, value) -> value != null)
                .to(
                    kafkaAdminService.getTopicName(Execution.class),
                    Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
                );
        }

        private void handleWorkerTask(KStream<String, TaskRunExecutionWithFlow> stream) {
            KStream<String, Either<WithException, WorkerTask>> streamEither = stream
                .filter((key, value) -> value.getTaskRun().getState().getCurrent() == State.Type.CREATED)
                .mapValues((readOnlyKey, taskRunExecutionWithFlow) -> {
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
                });

            branchException(streamEither)
                .to(
                    kafkaAdminService.getTopicName(WorkerTask.class),
                    Produced.with(Serdes.String(), JsonSerde.of(WorkerTask.class))
                );
        }

        private void handleNext(KStream<String, ExecutionWithFlow> stream) {
            stream
                .mapValues((readOnlyKey, value) -> {
                    List<TaskRun> next = FlowableUtils.resolveSequentialNexts(
                        value.getExecution(),
                        ResolvedTask.of(value.getFlow().getTasks()),
                        ResolvedTask.of(value.getFlow().getErrors())
                    );

                    if (next.size() > 0) {
                        return this.onNexts(value.getFlow(), value.getExecution(), next);
                    }

                    return null;
                })
                .filter((key, value) -> value != null)
                .to(
                    kafkaAdminService.getTopicName(Execution.class),
                    Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
                );
        }

        private <T> KStream<String, T> branchException(KStream<String, Either<WithException, T>> stream) {
            stream
                .filter((key, value) -> value.isLeft())
                .mapValues(Either::getLeft)
                .mapValues(e -> e.getEntity().getExecution().failedExecutionFromExecutor(e.getException()))
                .to(
                    kafkaAdminService.getTopicName(Execution.class),
                    Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
                );

            return stream
                .filter((key, value) -> value.isRight())
                .mapValues(Either::getRight);
        }

        @Override
        public void run() {
            KafkaStreamService.Stream resultStream = kafkaStreamService.of(this.getClass(), this.topology());
            resultStream.start();
        }
    }

    public interface ExecutionInterface {
        Execution getExecution();
    }

    @AllArgsConstructor
    @Getter
    public static class ExecutionWithFlow implements ExecutionInterface{
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
    public static  class WithException {
        ExecutionInterface entity;
        Exception exception;
    }

    @AllArgsConstructor
    public static class WorkerResultState implements Runnable {
        KafkaStreamService kafkaStreamService;
        KafkaAdminService kafkaAdminService;

        private Topology topology() {
            this.kafkaAdminService.createIfNotExist(WorkerTaskResult.class);
            this.kafkaAdminService.createIfNotExist(Execution.class);

            StreamsBuilder builder = new StreamsBuilder();

            builder
                .stream(
                    kafkaAdminService.getTopicName(WorkerTaskResult.class),
                    Consumed.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class))
                )
                .join(
                    builder.table(
                        kafkaAdminService.getTopicName(Execution.class),
                        Consumed.with(Serdes.String(), JsonSerde.of(Execution.class)),
                        Materialized.<String, Execution, KeyValueStore<Bytes, byte[]>>as("execution_join")
                            .withKeySerde(Serdes.String())
                            .withValueSerde(JsonSerde.of(Execution.class))
                    ),
                    (workerTaskResult, execution) -> {
                        try {
                            return execution.withTaskRun(workerTaskResult.getTaskRun());
                        } catch (Exception e) {
                            return execution.failedExecutionFromExecutor(e);
                        }
                    }
                )
                .to(
                    kafkaAdminService.getTopicName(Execution.class),
                    Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
                );

            return builder.build();
        }

        @Override
        public void run() {
            KafkaStreamService.Stream resultStream = kafkaStreamService.of(this.getClass(), this.topology());
            resultStream.start();
        }
    }

    @Override
    public void run() {
        kafkaAdminService.createIfNotExist(WorkerTaskResult.class);
        kafkaAdminService.createIfNotExist(Execution.class);

        new WorkerResultState(
            this.kafkaStreamService,
            this.kafkaAdminService
        ).run();

        new ExecutionState(
            this.applicationContext,
            this.kafkaStreamService,
            this.kafkaAdminService,
            this.flowRepository,
            this.metricRegistry
        ).run();
    }
}
