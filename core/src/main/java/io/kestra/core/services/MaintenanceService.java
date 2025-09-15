package io.kestra.core.services;

import jakarta.inject.Singleton;

@Singleton
public class MaintenanceService {
    /**
     * @return true if the cluster is in maintenance mode
     */
    public boolean isInMaintenanceMode() {
        // maintenance mode is an EE feature
        return false;
    }
}
