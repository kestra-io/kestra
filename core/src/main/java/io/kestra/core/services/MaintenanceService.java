package io.kestra.core.services;

import io.kestra.core.utils.Disposable;
import jakarta.inject.Singleton;
import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;

public interface MaintenanceService {

    /**
     * Checks whether the cluster is currently in maintenance mode.
     *
     * @return {@code true} if the cluster is in maintenance mode
     */
    boolean isInMaintenanceMode();

    /**
     * Retrieves the current maintenance mode status with detailed information.
     *
     * @return {@link Status} containing maintenance mode details
     */
    default Status getStatus() {
        return Status.builder()
            .isActive(isInMaintenanceMode())
            .build();
    }

    /**
     * Listens for cluster maintenance events.
     *
     * @param listener the listener.
     * @return a {@link Disposable} to called to stop listening to.
     */
    Disposable listen(final MaintenanceListener listener);

    /**
     * Interface for listening on maintenance events.
     */
    interface MaintenanceListener {
        /**
         * Invoked when cluster is entering maintenance mode.
         */
        void onMaintenanceModeEnter();

        /**
         * Invoked when cluster is exiting maintenance mode.
         */
        void onMaintenanceModeExit();
    }

    /**
     * Noop {@link MaintenanceService} implementation.
     *<p>
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
            return Disposable.of(() -> {}); // NOOP
        }
    }

    /**
     * Maintenance mode status details.
     */
    @Value
    @Builder(toBuilder = true)
    class Status {
        /**
         * Whether maintenance mode is currently active.
         */
        boolean isActive;

        /**
         * Reason for the maintenance.
         */
        String reason;

        /**
         * Time when maintenance mode was activated.
         */
        ZonedDateTime startTime;

        /**
         * Estimated time when maintenance will be completed.
         */
        ZonedDateTime estimatedEnd;
    }
}