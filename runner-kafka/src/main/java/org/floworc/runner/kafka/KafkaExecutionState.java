package org.floworc.runner.kafka;

import io.micronaut.context.annotation.Prototype;
import lombok.AllArgsConstructor;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.runners.ExecutionStateInterface;
import org.floworc.core.runners.RunContext;
import org.floworc.core.runners.WorkerTask;
import org.floworc.core.runners.WorkerTaskResult;
import org.floworc.runner.kafka.serializers.JsonSerde;
import org.floworc.runner.kafka.services.KafkaAdminService;
import org.floworc.runner.kafka.services.KafkaStreamService;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@KafkaQueueEnabled
@Prototype
public class KafkaExecutionState implements ExecutionStateInterface {
    @Inject
    KafkaStreamService kafkaStreamService;

    @Inject
    KafkaAdminService kafkaAdminService;

    @Inject
    FlowRepositoryInterface flowRepository;

    @AllArgsConstructor
    public static class ExecutionState implements Runnable {
        KafkaStreamService kafkaStreamService;
        KafkaAdminService kafkaAdminService;
        FlowRepositoryInterface flowRepository;

        private Topology topology() {
            kafkaAdminService.createIfNotExist(WorkerTaskResult.class);
            kafkaAdminService.createIfNotExist(Execution.class);

            StreamsBuilder builder = new StreamsBuilder();

            builder
                .stream(
                    kafkaAdminService.getTopicName(Execution.class),
                    Consumed.with(Serdes.String(), JsonSerde.of(Execution.class))
                )
                .flatMapValues(new WorkerTaskMapper(this.flowRepository))
                .to(
                    kafkaAdminService.getTopicName(WorkerTask.class),
                    Produced.with(Serdes.String(), JsonSerde.of(WorkerTask.class))
                );


            return builder.build();
        }

        @Override
        public void run() {
            KafkaStreamService.Stream resultStream = kafkaStreamService.of(this.getClass(), this.topology());
            resultStream.start();
        }

        static class WorkerTaskMapper implements ValueMapper<Execution, Iterable<WorkerTask>> {
            private FlowRepositoryInterface flowRepository;

            public WorkerTaskMapper(FlowRepositoryInterface flowRepository) {
                this.flowRepository = flowRepository;
            }

            @Override
            public Iterable<WorkerTask> apply(Execution execution) {
                Flow flow = this.flowRepository
                    .findByExecution(execution)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid flow id '" + execution.getFlowId() + "'"));

                List<TaskRun> nexts = execution
                    .getTaskRunList()
                    .stream()
                    .filter(taskRun -> taskRun.getState().getCurrent() == State.Type.CREATED)
                    .collect(Collectors.toList());

                List<WorkerTask> result = new ArrayList<>();

                for (TaskRun taskRun: nexts) {
                    Task task = flow.findTaskById(taskRun.getTaskId());

                    result.add(
                        WorkerTask.builder()
                            .runContext(new RunContext(flow, task, execution, taskRun))
                            .taskRun(taskRun)
                            .task(task)
                            .build()
                    );
                }

                return result;
            }
        }
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
