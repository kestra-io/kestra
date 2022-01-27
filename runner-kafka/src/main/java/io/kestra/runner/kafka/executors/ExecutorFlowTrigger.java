package io.kestra.runner.kafka.executors;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;
import io.kestra.core.services.FlowService;
import io.kestra.runner.kafka.KafkaQueueEnabled;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.kestra.runner.kafka.services.KafkaAdminService;
import io.kestra.runner.kafka.services.KafkaStreamSourceService;
import io.kestra.runner.kafka.services.KafkaStreamsBuilder;
import io.kestra.runner.kafka.streams.FlowTriggerWithExecutionTransformer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.Stores;

import java.util.stream.Collectors;

@KafkaQueueEnabled
@Singleton
public class ExecutorFlowTrigger implements KafkaExecutorInterface {
    public static final String TRIGGER_MULTIPLE_STATE_STORE_NAME = "trigger_multiplecondition";

    @Inject
    private KafkaAdminService kafkaAdminService;

    @Inject
    private FlowService flowService;

    @Inject
    private KafkaStreamSourceService kafkaStreamSourceService;

    public StreamsBuilder topology() {
        StreamsBuilder builder = new KafkaStreamsBuilder();

        kafkaStreamSourceService.flowGlobalKTable(builder);

        // trigger
        builder.addStateStore(
            Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(TRIGGER_MULTIPLE_STATE_STORE_NAME),
                Serdes.String(),
                JsonSerde.of(MultipleConditionWindow.class)
            )
        );

        KStream<String, io.kestra.runner.kafka.streams.ExecutorFlowTrigger> stream = builder
            .stream(
                kafkaAdminService.getTopicName(io.kestra.runner.kafka.streams.ExecutorFlowTrigger.class),
                Consumed.with(Serdes.String(), JsonSerde.of(io.kestra.runner.kafka.streams.ExecutorFlowTrigger.class))
                    .withName("KStream.ExecutorFlowTrigger")
            )
            .filter((key, value) -> value != null, Named.as("ExecutorFlowTrigger.filterNotNull"));

        stream
            .transformValues(
                () -> new FlowTriggerWithExecutionTransformer(
                    TRIGGER_MULTIPLE_STATE_STORE_NAME,
                    flowService
                ),
                Named.as("ExecutorFlowTrigger.transformToExecutionList"),
                TRIGGER_MULTIPLE_STATE_STORE_NAME
            )
            .flatMap(
                (key, value) -> value
                    .stream()
                    .map(execution -> new KeyValue<>(execution.getId(), execution))
                    .collect(Collectors.toList()),
                Named.as("ExecutorFlowTrigger.flapMapToExecution")
            )
            .to(
                kafkaAdminService.getTopicName(Execution.class),
                Produced
                    .with(Serdes.String(), JsonSerde.of(Execution.class))
                    .withName("ExecutorFlowTrigger.toExecution")
            );

        stream
            .mapValues(
                (readOnlyKey, value) -> (io.kestra.runner.kafka.streams.ExecutorFlowTrigger)null,
                Named.as("ExecutorFlowTrigger.executorFlowTriggerToNull")
            )
            .to(
                kafkaAdminService.getTopicName(io.kestra.runner.kafka.streams.ExecutorFlowTrigger.class),
                Produced
                    .with(Serdes.String(), JsonSerde.of(io.kestra.runner.kafka.streams.ExecutorFlowTrigger.class))
                    .withName("ExecutorFlowTrigger.toExecutorFlowTrigger")
            );

        return builder;
    }
}
