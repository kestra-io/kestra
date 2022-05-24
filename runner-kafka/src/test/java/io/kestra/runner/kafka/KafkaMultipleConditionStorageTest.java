package io.kestra.runner.kafka;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.AbstractMultipleConditionStorageTest;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;

import java.util.*;

class KafkaMultipleConditionStorageTest extends AbstractMultipleConditionStorageTest {
    MockKeyValueStore<String, MultipleConditionWindow> store;

    protected MultipleConditionStorageInterface multipleConditionStorage() {
        store = new MockKeyValueStore<>("test");
        return new KafkaMultipleConditionStorage(store);
    }

    protected void save(MultipleConditionStorageInterface multipleConditionStorage, Flow flow, List<MultipleConditionWindow> multipleConditionWindows) {
        multipleConditionWindows.forEach(multipleConditionWindow -> {
            store.put(
                MultipleConditionWindow.uid(flow, multipleConditionWindow.getConditionId()),
                multipleConditionWindow
            );
        });
    }
}