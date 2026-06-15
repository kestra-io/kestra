package io.kestra.core.services;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.MaintenanceStatusResponse;
import io.kestra.core.models.MaintenanceStatusResponse.ServiceStatus;
import io.kestra.core.server.Service.ServiceState;
import io.kestra.core.server.ServerInstance;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Disposable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MaintenanceServiceTest {

    private final ServiceLivenessStore livenessStore = mock(ServiceLivenessStore.class);

    @Test
    void shouldReturnNotInMaintenanceWhenNoop() {
        // Given
        MaintenanceService service = new MaintenanceService.NoopMaintenanceService();
        when(livenessStore.findAllInstancesInStates(ServiceState.allRunningStates())).thenReturn(List.of());

        // When
        MaintenanceStatusResponse response = service.getMaintenanceStatus(livenessStore);

        // Then
        assertThat(response.maintenance()).isFalse();
        assertThat(response.ready()).isFalse();
        assertThat(response.services()).isEmpty();
    }

    @Test
    void shouldReturnNotReadyWhenInMaintenanceButServicesStillRunning() {
        // Given
        MaintenanceService service = maintenanceService(true);
        when(livenessStore.findAllInstancesInStates(ServiceState.allRunningStates())).thenReturn(List.of(
            serviceInstance(ServiceType.WORKER, ServiceState.RUNNING),
            serviceInstance(ServiceType.WORKER, ServiceState.MAINTENANCE),
            serviceInstance(ServiceType.EXECUTOR, ServiceState.RUNNING)
        ));

        // When
        MaintenanceStatusResponse response = service.getMaintenanceStatus(livenessStore);

        // Then
        assertThat(response.maintenance()).isTrue();
        assertThat(response.ready()).isFalse();
        assertThat(response.services()).containsKey(ServiceType.WORKER);
        assertThat(response.services()).containsKey(ServiceType.EXECUTOR);

        ServiceStatus workerStatus = response.services().get(ServiceType.WORKER);
        assertThat(workerStatus.total()).isEqualTo(2);
        assertThat(workerStatus.inMaintenance()).isEqualTo(1);

        ServiceStatus executorStatus = response.services().get(ServiceType.EXECUTOR);
        assertThat(executorStatus.total()).isEqualTo(1);
        assertThat(executorStatus.inMaintenance()).isEqualTo(0);
    }

    @Test
    void shouldReturnReadyWhenAllServicesInMaintenance() {
        // Given
        MaintenanceService service = maintenanceService(true);
        when(livenessStore.findAllInstancesInStates(ServiceState.allRunningStates())).thenReturn(List.of(
            serviceInstance(ServiceType.WORKER, ServiceState.MAINTENANCE),
            serviceInstance(ServiceType.EXECUTOR, ServiceState.MAINTENANCE)
        ));

        // When
        MaintenanceStatusResponse response = service.getMaintenanceStatus(livenessStore);

        // Then
        assertThat(response.maintenance()).isTrue();
        assertThat(response.ready()).isTrue();
        assertThat(response.services().get(ServiceType.WORKER).inMaintenance()).isEqualTo(1);
        assertThat(response.services().get(ServiceType.EXECUTOR).inMaintenance()).isEqualTo(1);
    }

    @Test
    void shouldReturnNotReadyWhenInMaintenanceButNoServices() {
        // Given
        MaintenanceService service = maintenanceService(true);
        when(livenessStore.findAllInstancesInStates(ServiceState.allRunningStates())).thenReturn(List.of());

        // When
        MaintenanceStatusResponse response = service.getMaintenanceStatus(livenessStore);

        // Then
        assertThat(response.maintenance()).isTrue();
        assertThat(response.ready()).isFalse();
        assertThat(response.services()).isEmpty();
    }

    private static MaintenanceService maintenanceService(boolean inMaintenance) {
        return new MaintenanceService() {
            @Override
            public boolean isInMaintenanceMode() {
                return inMaintenance;
            }

            @Override
            public Disposable listen(MaintenanceListener listener) {
                return Disposable.of(() -> {});
            }
        };
    }

    private static ServiceInstance serviceInstance(ServiceType type, ServiceState state) {
        return new ServiceInstance(
            "test-" + type.name().toLowerCase(),
            type,
            state,
            new ServerInstance("server-1", ServerInstance.Type.STANDALONE, "1.0", "localhost", null, null),
            Instant.now(),
            Instant.now(),
            null,
            null,
            null,
            null,
            0L
        );
    }
}
