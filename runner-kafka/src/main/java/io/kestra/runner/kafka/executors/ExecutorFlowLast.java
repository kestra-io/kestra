package io.kestra.runner.kafka.executors;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.queues.QueueService;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.ListUtils;
import io.kestra.runner.kafka.KafkaQueueEnabled;
import io.kestra.runner.kafka.serializers.JsonSerde;
import io.kestra.runner.kafka.services.KafkaAdminService;
import io.kestra.runner.kafka.services.KafkaStreamSourceService;
import io.kestra.runner.kafka.services.KafkaStreamsBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@KafkaQueueEnabled
@Singleton
@Slf4j
public class ExecutorFlowLast implements KafkaExecutorInterface {
    @Inject
    private KafkaAdminService kafkaAdminService;

    @Inject
    private QueueService queueService;

    public StreamsBuilder topology() {
        StreamsBuilder builder = new KafkaStreamsBuilder();

        // last global KTable
        GlobalKTable<String, Flow> flowGlobalKTable = builder
            .globalTable(
                kafkaAdminService.getTopicName(KafkaStreamSourceService.TOPIC_FLOWLAST),
                Consumed.with(Serdes.String(), JsonSerde.of(Flow.class)).withName("GlobalKTable.FlowLast"),
                Materialized.<String, Flow, KeyValueStore<Bytes, byte[]>>as("last")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Flow.class))
            );

        // stream
        KStream<String, Flow> stream = builder
            .stream(
                kafkaAdminService.getTopicName(Flow.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Flow.class, false)).withName("Stream.Flow")
            );

        // logs
        stream = KafkaStreamSourceService.logIfEnabled(
            log,
            stream,
            (key, value) -> log.trace(
                "Flow in '{}.{}' with revision {}",
                value.getNamespace(),
                value.getId(),
                value.getRevision()
            ),
            "Main"
        );

        // join with previous if more recent revision
        KStream<String, ExecutorFlowLast.FlowWithPrevious> streamWithPrevious = stream
            .filter((key, value) -> value != null, Named.as("Main.notNull"))
            .selectKey((key, value) -> value.uidWithoutRevision(), Named.as("Main.selectKey"))
            .leftJoin(
                flowGlobalKTable,
                (key, value) -> key,
                (readOnlyKey, current, previous) -> {
                    if (previous == null) {
                        return new ExecutorFlowLast.FlowWithPrevious(current, null);
                    } else if (current.getRevision() < previous.getRevision()) {
                        return null;
                    } else {
                        return new ExecutorFlowLast.FlowWithPrevious(current, previous);
                    }
                },
                Named.as("Main.join")
            )
            .filter((key, value) -> value != null, Named.as("Main.joinNotNull"));

        // remove triggers
        streamWithPrevious
            .flatMap(
                (key, value) -> {
                    List<AbstractTrigger> deletedTriggers = new ArrayList<>();

                    if (value.getFlow().isDeleted()) {
                        deletedTriggers = ListUtils.emptyOnNull(value.getFlow().getTriggers());
                    } else if (value.getPrevious() != null) {
                        deletedTriggers = FlowService.findRemovedTrigger(
                            value.getFlow(),
                            value.getPrevious()
                        );
                    }

                    return deletedTriggers
                        .stream()
                        .map(t -> new KeyValue<>(
                            queueService.key(Trigger.of(value.getFlow(), t)),
                            (Trigger) null
                        ))
                        .collect(Collectors.toList());
                },
                Named.as("DeleteTrigger.flatMap")
            )
            .to(
                kafkaAdminService.getTopicName(Trigger.class),
                Produced.with(Serdes.String(), JsonSerde.of(Trigger.class)).withName("To.Trigger")
            );

        // send to last and don't drop deleted flow in order to keep last version
        streamWithPrevious
            .map(
                (key, value) -> new KeyValue<>(
                    value.getFlow().uidWithoutRevision(),
                    value.getFlow()
                ),
                Named.as("Main.Map")
            )
            .to(
                kafkaAdminService.getTopicName(KafkaStreamSourceService.TOPIC_FLOWLAST),
                Produced.with(Serdes.String(), JsonSerde.of(Flow.class)).withName("To.FlowLast")
            );

        return builder;
    }

    @Getter
    @AllArgsConstructor
    public static class FlowWithPrevious {
        private Flow flow;
        private Flow previous;
    }
}
