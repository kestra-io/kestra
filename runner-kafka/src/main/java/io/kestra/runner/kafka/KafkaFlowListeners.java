package io.kestra.runner.kafka;

import io.kestra.runner.kafka.services.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.runner.kafka.serializers.JsonSerde;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Slf4j
@KafkaQueueEnabled
public class KafkaFlowListeners implements FlowListenersInterface {
    private final KafkaAdminService kafkaAdminService;
    private final KafkaStreamService kafkaStreamService;
    private SafeKeyValueStore<String, Flow> store;
    private final List<Consumer<List<Flow>>> consumers = new ArrayList<>();
    private KafkaStreamService.Stream stream;
    private List<Flow> flows;

    @Inject
    public KafkaFlowListeners(KafkaAdminService kafkaAdminService, KafkaStreamService kafkaStreamService) {
        this.kafkaAdminService = kafkaAdminService;
        this.kafkaStreamService = kafkaStreamService;
    }

    @Override
    public void run() {
        kafkaAdminService.createIfNotExist(KafkaStreamSourceService.TOPIC_FLOWLAST);

        stream = kafkaStreamService.of(FlowListener.class, FlowListener.class, new FlowListener().topology(), log);
        stream.start((newState, oldState) -> {
            if (newState == KafkaStreams.State.RUNNING) {
                try {
                    ReadOnlyKeyValueStore<String, Flow> store = stream.store(StoreQueryParameters.fromNameAndType(
                        "flow",
                        QueryableStoreTypes.keyValueStore()
                    ));

                    this.store = new SafeKeyValueStore<>(store, "flow");
                    this.send(this.flows());
                } catch (InvalidStateStoreException e) {
                    this.store = null;
                    log.warn(e.getMessage(), e);
                }
            } else {
                synchronized (this) {
                    flows = null;
                }
                this.send(new ArrayList<>());
            }
        });
    }

    public class FlowListener {
        public Topology topology() {
            StreamsBuilder builder = new KafkaStreamsBuilder();

            builder
                .table(
                    kafkaAdminService.getTopicName(KafkaStreamSourceService.TOPIC_FLOWLAST),
                    Consumed.with(Serdes.String(), JsonSerde.of(Flow.class, false)),
                    Materialized.<String, Flow, KeyValueStore<Bytes, byte[]>>as("flow")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(JsonSerde.of(Flow.class, false))
                )
                .filter((key, value) -> value != null)
                .toStream()
                .peek((key, value) -> {
                    synchronized (this) {
                        flows = null;
                    }

                    send(flows());
                });

            Topology topology = builder.build();

            if (log.isTraceEnabled()) {
                log.trace(topology.describe().toString());
            }

            return topology;
        }
    }

    @Override
    public List<Flow> flows() {
        if (this.store == null || stream.state() != KafkaStreams.State.RUNNING) {
            return Collections.emptyList();
        }

        synchronized (this) {
            if (this.flows == null) {
                this.flows = this.store
                    .toStream()
                    .filter(flow -> flow != null && !flow.isDeleted())
                    .collect(Collectors.toList());
            }
        }

        //noinspection ConstantConditions
        return this.flows == null ? new ArrayList<>() : this.flows;
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
