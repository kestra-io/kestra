package org.kestra.runner.kafka;

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
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.*;
import org.kestra.runner.kafka.serializers.JsonSerde;
import org.kestra.runner.kafka.services.KafkaAdminService;
import org.kestra.runner.kafka.services.KafkaStreamService;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@KafkaQueueEnabled
@Prototype
public class KafkaExecutor extends AbstractExecutor {
    @Inject
    KafkaStreamService kafkaStreamService;

    @Inject
    KafkaAdminService kafkaAdminService;

    @Inject
    FlowRepositoryInterface flowRepository;

    @AllArgsConstructor
    public static class ExecutionState extends AbstractExecutor {
        KafkaStreamService kafkaStreamService;
        KafkaAdminService kafkaAdminService;
        FlowRepositoryInterface flowRepository;

        private Topology topology() {
            kafkaAdminService.createIfNotExist(WorkerTaskResult.class);
            kafkaAdminService.createIfNotExist(Execution.class);

            StreamsBuilder builder = new StreamsBuilder();

            // with flow
            KStream<String, ExecutionWithFlow> stream = builder
                .stream(
                    kafkaAdminService.getTopicName(Execution.class),
                    Consumed.with(Serdes.String(), JsonSerde.of(Execution.class))
                )
                .mapValues((readOnlyKey, execution) -> {
                    Flow flow = this.flowRepository
                        .findByExecution(execution)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid flow id '" + execution.getFlowId() + "'"));

                    return new ExecutionWithFlow(flow, execution);
                });

            // on End
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

            // Listeners
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

            // with taskrun
            KStream<String, TaskRunExecutionWithFlow> streamTaskRuns = stream
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

            // handlChild WorkerTaskResult
            streamTaskRuns
                .filter((key, value) -> value.getTaskRun().getState().getCurrent() == State.Type.RUNNING)
                .mapValues((readOnlyKey, value) -> this.childWorkerTaskResult(value.getFlow(), value.getExecution(), value.getTaskRun()).orElse(null))
                .filter((key, value) -> value != null)
                .to(
                    kafkaAdminService.getTopicName(WorkerTaskResult.class),
                    Produced.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class))
                );

            // handlChild nexts
            streamTaskRuns
                .filter((key, value) -> value.getTaskRun().getState().getCurrent() == State.Type.RUNNING)
                .mapValues((readOnlyKey, value) -> this.childNexts(value.getFlow(), value.getExecution(), value.getTaskRun()).orElse(null))
                .filter((key, value) -> value != null)
                .to(
                    kafkaAdminService.getTopicName(Execution.class),
                    Produced.with(Serdes.String(), JsonSerde.of(Execution.class))
                );

            // Worker task
            streamTaskRuns
                .filter((key, value) -> value.getTaskRun().getState().getCurrent() == State.Type.CREATED)
                .mapValues((readOnlyKey, taskRunExecutionWithFlow) -> {
                    ResolvedTask resolvedTask = taskRunExecutionWithFlow.getFlow().findTaskByTaskRun(
                        taskRunExecutionWithFlow.getTaskRun(),
                        new RunContext(taskRunExecutionWithFlow.getFlow(), taskRunExecutionWithFlow.getExecution())
                    );

                    return WorkerTask.builder()
                        .runContext(new RunContext(
                            taskRunExecutionWithFlow.getFlow(),
                            resolvedTask,
                            taskRunExecutionWithFlow.getExecution(),
                            taskRunExecutionWithFlow.getTaskRun())
                        )
                        .taskRun(taskRunExecutionWithFlow.getTaskRun())
                        .task(resolvedTask.getTask())
                        .build();
                })
                .to(
                    kafkaAdminService.getTopicName(WorkerTask.class),
                    Produced.with(Serdes.String(), JsonSerde.of(WorkerTask.class))
                );

            // On next
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


            return builder.build();
        }

        @Override
        public void run() {
            KafkaStreamService.Stream resultStream = kafkaStreamService.of(this.getClass(), this.topology());
            resultStream.start();
        }
    }

    @AllArgsConstructor
    @Getter
    public static class ExecutionWithFlow {
        Flow flow;
        Execution execution;
    }


    @AllArgsConstructor
    @Getter
    public static class TaskRunExecutionWithFlow {
        Flow flow;
        Execution execution;
        TaskRun taskRun;
    }

    @AllArgsConstructor
    public static class WorkerResultState implements Runnable {
        KafkaStreamService kafkaStreamService;
        KafkaAdminService kafkaAdminService;

        private Topology topology() {
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
                    (workerTaskResult, execution) -> execution.withTaskRun(workerTaskResult.getTaskRun())
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
            this.kafkaStreamService,
            this.kafkaAdminService,
            this.flowRepository
        ).run();

    }
}
