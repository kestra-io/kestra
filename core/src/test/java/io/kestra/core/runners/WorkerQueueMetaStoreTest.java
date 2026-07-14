package io.kestra.core.runners;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceType;
import io.kestra.core.worker.WorkerGroups;
import io.kestra.core.worker.WorkerQueues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerQueueMetaStoreTest {

    @Test
    void shouldKeepImplicitQueuesActiveWithoutLivenessStore() {
        // Given
        WorkerQueueMetaStore.DefaultWorkerQueueMetaStore metaStore = new WorkerQueueMetaStore.DefaultWorkerQueueMetaStore();

        // When - Then
        assertThat(metaStore.hasActiveWorkerForQueue(null)).isTrue();
        assertThat(metaStore.hasActiveWorkerForQueue(WorkerQueues.DEFAULT_ID)).isTrue();
        assertThat(metaStore.hasActiveWorkerForQueue(WorkerQueues.SYSTEM_ID)).isTrue();
        assertThat(metaStore.hasActiveWorkerForQueue("named")).isTrue();
        assertThat(metaStore.listAllWorkerQueueIds()).isEmpty();
    }

    @Test
    void shouldCacheActiveWorkerQueueSnapshot() {
        // Given
        ServiceLivenessStore livenessStore = mock(ServiceLivenessStore.class);
        when(livenessStore.findAllInstancesInStates(Service.ServiceState.allRunningStates()))
            .thenReturn(
                List.of(
                    workerInstance("worker-a", "group-a"),
                    workerInstance("worker-default", WorkerGroups.DEFAULT_ID),
                    serviceInstance("executor", ServiceType.EXECUTOR, "group-b")
                )
            );
        WorkerQueueMetaStore.DefaultWorkerQueueMetaStore metaStore = new WorkerQueueMetaStore.DefaultWorkerQueueMetaStore(livenessStore, Duration.ofMinutes(1));

        // When - Then
        assertThat(metaStore.hasActiveWorkerForQueue("group-a")).isTrue();
        assertThat(metaStore.hasActiveWorkerForQueue("group-b")).isFalse();
        assertThat(metaStore.listAllWorkerQueueIds()).containsExactly("group-a");
        verify(livenessStore, times(1)).findAllInstancesInStates(Service.ServiceState.allRunningStates());
    }

    @Test
    void shouldInvalidateCacheOnWorkerLivenessUpdate() {
        // Given
        ServiceLivenessStore livenessStore = mock(ServiceLivenessStore.class);
        when(livenessStore.findAllInstancesInStates(Service.ServiceState.allRunningStates()))
            .thenReturn(List.of(workerInstance("worker-a", "group-a")))
            .thenReturn(List.of(workerInstance("worker-b", "group-b")));
        WorkerQueueMetaStore.DefaultWorkerQueueMetaStore metaStore = new WorkerQueueMetaStore.DefaultWorkerQueueMetaStore(livenessStore, Duration.ofMinutes(1));

        // When - Then
        assertThat(metaStore.hasActiveWorkerForQueue("group-a")).isTrue();

        metaStore.onLivenessUpdate(Instant.now(), workerInstance("worker-a", "group-a"), Service.ServiceState.RUNNING);

        assertThat(metaStore.hasActiveWorkerForQueue("group-a")).isFalse();
        assertThat(metaStore.hasActiveWorkerForQueue("group-b")).isTrue();
        verify(livenessStore, times(2)).findAllInstancesInStates(Service.ServiceState.allRunningStates());
    }

    @Test
    void shouldAvoidRepeatedStoreReadsForRepeatedRoutingChecks() {
        // Given
        ServiceLivenessStore livenessStore = mock(ServiceLivenessStore.class);
        when(livenessStore.findAllInstancesInStates(Service.ServiceState.allRunningStates()))
            .thenReturn(List.of(workerInstance("worker-a", "group-a")));
        WorkerQueueMetaStore.DefaultWorkerQueueMetaStore metaStore = new WorkerQueueMetaStore.DefaultWorkerQueueMetaStore(livenessStore, Duration.ofMinutes(1));

        // When
        for (int i = 0; i < 10_000; i++) {
            assertThat(metaStore.hasActiveWorkerForQueue("group-a")).isTrue();
        }

        // Then
        verify(livenessStore, times(1)).findAllInstancesInStates(Service.ServiceState.allRunningStates());
    }

    private static ServiceInstance workerInstance(String id, String workerGroupId) {
        return serviceInstance(id, ServiceType.WORKER, workerGroupId);
    }

    private static ServiceInstance serviceInstance(String id, ServiceType type, String workerGroupId) {
        return new ServiceInstance(
            id,
            type,
            Service.ServiceState.RUNNING,
            null,
            Instant.now(),
            Instant.now(),
            null,
            null,
            Map.of(WorkerGroups.SERVICE_PROPS_KEY, workerGroupId),
            null
        );
    }
}
