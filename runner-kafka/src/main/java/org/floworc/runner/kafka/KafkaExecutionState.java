package org.floworc.runner.kafka;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.runners.ExecutionStateInterface;
import org.floworc.core.runners.WorkerTaskResult;
import org.floworc.runner.kafka.serializers.JsonSerde;
import org.floworc.runner.kafka.services.KafkaAdminService;
import org.floworc.runner.kafka.services.KafkaStreamService;

import javax.inject.Inject;

@Slf4j
@KafkaQueueEnabled
@Prototype
public class KafkaExecutionState implements ExecutionStateInterface {
    @Inject
    KafkaStreamService kafkaStreamService;

    @Inject
    KafkaAdminService kafkaAdminService;

    private Topology topology() {
        kafkaAdminService.createIfNotExist(WorkerTaskResult.class);
        kafkaAdminService.createIfNotExist(Execution.class);

        StreamsBuilder builder = new StreamsBuilder();

        builder
            .stream(
                kafkaAdminService.getTopicName(WorkerTaskResult.class),
                Consumed.with(Serdes.String(), JsonSerde.of(WorkerTaskResult.class))
            )
            .leftJoin(
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
        KafkaStreamService.Stream stream = kafkaStreamService.of(KafkaExecutionState.class, this.topology());
        stream.start();
    }
}
