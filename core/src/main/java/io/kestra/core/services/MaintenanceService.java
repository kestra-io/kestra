package io.kestra.core.services;

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
}
