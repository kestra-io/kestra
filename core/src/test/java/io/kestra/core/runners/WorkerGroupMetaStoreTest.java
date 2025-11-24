package io.kestra.core.runners;

import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@KestraTest
class WorkerGroupMetaStoreTest {
    @Inject
    private WorkerGroupMetaStore workerGroupMetaStore;

    @Test
    void isWorkerGroupAvailableForKey() {
        boolean available = workerGroupMetaStore.isWorkerGroupAvailableForKey("toto");

        assertTrue(available);
    }

    @Test
    void isWorkerGroupExistForKey() {
        boolean available = workerGroupMetaStore.isWorkerGroupExistForKey("key", "tenant");

        assertTrue(available);
    }

    @Test
    void listAllWorkerGroupKeys() {
        Set<String> keys = workerGroupMetaStore.listAllWorkerGroupKeys();

        assertThat(keys).isEmpty();
    }
}