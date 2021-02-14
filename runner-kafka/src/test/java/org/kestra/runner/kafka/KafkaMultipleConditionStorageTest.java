package org.kestra.runner.kafka;

import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionStorageTest;
import org.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;

import java.util.*;

class KafkaMultipleConditionStorageTest extends MultipleConditionStorageTest {
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