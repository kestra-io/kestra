package org.kestra.runner.kafka;

import org.apache.kafka.streams.state.KeyValueStore;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;

import java.util.Optional;

public class KafkaMultipleConditionStorage implements MultipleConditionStorageInterface {
    private final KeyValueStore<String, MultipleConditionWindow> store;

    public KafkaMultipleConditionStorage(KeyValueStore<String, MultipleConditionWindow> store) {
        this.store = store;
    }

    @Override
    public Optional<MultipleConditionWindow> get(Flow flow, String conditionId) {
        return Optional.ofNullable(this.store.get(MultipleConditionWindow.uid(flow, conditionId)));
    }
}
