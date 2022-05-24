package io.kestra.runner.memory;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.multipleflows.AbstractMultipleConditionStorageTest;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionWindow;

import java.util.List;

class MemoryMultipleConditionStorageTest extends AbstractMultipleConditionStorageTest {
    protected MultipleConditionStorageInterface multipleConditionStorage() {
        return new MemoryMultipleConditionStorage();
    }

    protected void save(MultipleConditionStorageInterface multipleConditionStorage, Flow flow, List<MultipleConditionWindow> multipleConditionWindows) {
        ((MemoryMultipleConditionStorage) multipleConditionStorage).save(multipleConditionWindows);
    }
}