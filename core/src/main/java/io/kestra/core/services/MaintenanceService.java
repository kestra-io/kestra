package io.kestra.core.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.kestra.core.models.MaintenanceStatusResponse;
import io.kestra.core.models.MaintenanceStatusResponse.ServiceStatus;
import io.kestra.core.server.Service.ServiceState;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Disposable;

import jakarta.inject.Singleton;

public interface MaintenanceService {

    /**
     * Checks whether the cluster is currently in maintenance mode.
     *
     * @return {@code true} if the cluster is in maintenance mode
     */
    boolean isInMaintenanceMode();

    /**
     * Listens for cluster maintenance events.
     *
     * @param listener the listener.
     * @return a {@link Disposable} to called to stop listening to.
     */
    Disposable listen(final MaintenanceListener listener);

    /**
     * Builds the current maintenance status including per-service readiness.
     *
     * @param livenessStore the store to query running service instances.
     * @return a {@link MaintenanceStatusResponse}.
     */
    default MaintenanceStatusResponse getMaintenanceStatus(ServiceLivenessStore livenessStore) {
        boolean maintenance = isInMaintenanceMode();
        List<ServiceInstance> allRunning = livenessStore.findAllInstancesInStates(ServiceState.allRunningStates());

        Map<ServiceType, ServiceStatus> services = allRunning.stream()
            .collect(Collectors.groupingBy(ServiceInstance::type))
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    int inMaintenance = (int) entry.getValue().stream()
                        .filter(si -> ServiceState.MAINTENANCE.equals(si.state()))
                        .count();
                    return new ServiceStatus(entry.getValue().size(), inMaintenance);
                },
                (a, b) -> a,
                LinkedHashMap::new
            ));

        boolean ready = maintenance && !allRunning.isEmpty() && allRunning.stream()
            .allMatch(si -> ServiceState.MAINTENANCE.equals(si.state()));

        return new MaintenanceStatusResponse(maintenance, ready, services);
    }

    /**
     * Interface for listening on maintenance events.
     */
    interface MaintenanceListener {
        /**
         * Invoked when the cluster is entering maintenance mode.
         */
        void onMaintenanceModeEnter();

        /**
         * Invoked when the cluster is exiting maintenance mode.
         */
        void onMaintenanceModeExit();
    }

    /**
     * Noop {@link MaintenanceService} implementation.
     * <p>
     * Maintenance mode is EE feature.
     */
    @Singleton
    class NoopMaintenanceService implements MaintenanceService {

        @Override
        public boolean isInMaintenanceMode() {
            return false;
        }

        @Override
        public Disposable listen(MaintenanceListener listener) {
            return Disposable.of(() ->
            {
            }); // NOOP
        }
    }
}
