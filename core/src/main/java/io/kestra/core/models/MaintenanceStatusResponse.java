package io.kestra.core.models;

import java.util.Map;

import io.kestra.core.server.ServiceType;

/**
 * Response payload for the maintenance status endpoint.
 *
 * @param maintenance whether maintenance mode is currently enabled.
 * @param ready       whether all services have transitioned to MAINTENANCE state
 *                    (i.e. the instance is safe to update).
 * @param services    per-type breakdown: how many instances of each service type
 *                    are in MAINTENANCE vs total running.
 */
public record MaintenanceStatusResponse(
    boolean maintenance,
    boolean ready,
    Map<ServiceType, ServiceStatus> services
) {

    /**
     * Status of a single service type.
     *
     * @param total         total number of running instances of this type.
     * @param inMaintenance number of instances that have reached MAINTENANCE state.
     */
    public record ServiceStatus(int total, int inMaintenance) {
    }
}
