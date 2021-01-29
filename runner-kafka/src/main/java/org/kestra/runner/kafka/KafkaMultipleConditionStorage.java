package org.kestra.runner.kafka;

import com.google.common.collect.Streams;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;

import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@KafkaQueueEnabled
public class KafkaMultipleConditionStorage implements MultipleConditionStorageInterface {
    private final ReadOnlyKeyValueStore<String, MultipleConditionWindow> store;

    public KafkaMultipleConditionStorage(ReadOnlyKeyValueStore<String, MultipleConditionWindow> store) {
        this.store = store;
    }

    @Override
    public Optional<MultipleConditionWindow> get(Flow flow, String conditionId) {

        log.info("All conditions: {}", Streams.stream(this.store.all()).collect(Collectors.toList()));

        log.info("Current one: {}", this.store.get(MultipleConditionWindow.uid(flow, conditionId)));

        return Optional.ofNullable(this.store.get(MultipleConditionWindow.uid(flow, conditionId)));
    }
}
