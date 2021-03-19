package io.kestra.runner.kafka;

import com.google.common.collect.Streams;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class KafkaMultipleConditionStorage implements MultipleConditionStorageInterface {
    private final KeyValueStore<String, MultipleConditionWindow> store;

    public KafkaMultipleConditionStorage(KeyValueStore<String, MultipleConditionWindow> store) {
        this.store = store;
    }

    @Override
    public Optional<MultipleConditionWindow> get(Flow flow, String conditionId) {
        return Optional.ofNullable(this.store.get(MultipleConditionWindow.uid(flow, conditionId)));
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public List<MultipleConditionWindow> expired() {
        ZonedDateTime now = ZonedDateTime.now();

        try (KeyValueIterator<String, MultipleConditionWindow> all = this.store.all()) {
            return Streams.stream(all)
                .map(e -> e.value)
                .filter(e -> !e.isValid(now))
                .collect(Collectors.toList());
        }
    }
}
