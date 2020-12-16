package org.kestra.runner.kafka;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.services.FlowListenersInterface;
import org.kestra.core.services.FlowService;
import org.kestra.runner.kafka.serializers.JsonSerde;
import org.kestra.runner.kafka.services.KafkaAdminService;
import org.kestra.runner.kafka.services.KafkaStreamService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Slf4j
@KafkaQueueEnabled
public class KafkaFlowListeners implements FlowListenersInterface {
    private final KafkaAdminService kafkaAdminService;
    private final FlowService flowService;

    private ReadOnlyKeyValueStore<String, Flow> store;
    private final List<Consumer<List<Flow>>> consumers = new ArrayList<>();
    private final KafkaStreamService.Stream stream;

    @Inject
    public KafkaFlowListeners(
        KafkaAdminService kafkaAdminService,
        KafkaStreamService kafkaStreamService,
        FlowService flowService
    ) {
        this.kafkaAdminService = kafkaAdminService;
        this.flowService = flowService;

        kafkaAdminService.createIfNotExist(Flow.class);

        stream = kafkaStreamService.of(this.getClass(), this.topology());
        stream.start((newState, oldState) -> {
            if (newState == KafkaStreams.State.RUNNING) {
                try {
                    this.store = stream.store("flow", QueryableStoreTypes.keyValueStore());
                    this.send(this.flows());
                } catch (InvalidStateStoreException e) {
                    this.store = null;
                    log.warn(e.getMessage(), e);
                }
            } else {
                this.send(new ArrayList<>());
            }
        });
    }

    public Topology topology() {
        StreamsBuilder builder = new StreamsBuilder();

        builder
            .table(
                kafkaAdminService.getTopicName(Flow.class),
                Consumed.with(Serdes.String(), JsonSerde.of(Flow.class)),
                Materialized.<String, Flow, KeyValueStore<Bytes, byte[]>>as("flow")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(JsonSerde.of(Flow.class))
            )
            .toStream()
            .peek((key, value) -> {
                this.send(this.flows());
            });

        Topology topology = builder.build();

        if (log.isTraceEnabled()) {
            log.trace(topology.describe().toString());
        }

        return topology;
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public List<Flow> flows() {
        if (this.store == null || stream.state() != KafkaStreams.State.RUNNING) {
            return Collections.emptyList();
        }

        try (KeyValueIterator<String, Flow> all = this.store.all()) {
            List<Flow> alls = Streams.stream(all).map(r -> r.value).collect(Collectors.toList());

            return flowService
                .keepLastVersion(alls)
                .stream()
                .filter(flow -> !flow.isDeleted())
                .collect(Collectors.toList());
        }
    }

    private void send(List<Flow> flows) {
        this.consumers
            .forEach(consumer -> consumer.accept(flows));
    }

    @Override
    public void listen(Consumer<List<Flow>> consumer) {
        consumers.add(consumer);
        consumer.accept(this.flows());
    }
}
