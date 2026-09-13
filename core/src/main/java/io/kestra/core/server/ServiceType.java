package io.kestra.core.server;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

/**
 * Supported Kestra's service types.
 */
public enum ServiceType {
    EXECUTOR,
    INDEXER,
    SCHEDULER,
    WEBSERVER,
    WORKER,
    SYSTEM_WORKER,
    CONTROLLER,
    INVALID;

    /**
     * Checks whether this type denotes a service executing worker jobs, whatever the worker flavour.
     * <p>
     * Liveness handling must use this rather than comparing to {@link #WORKER}, otherwise the jobs
     * of a crashed {@link #SYSTEM_WORKER} would never be re-emitted.
     *
     * @return {@code true} if this type is a worker.
     */
    public boolean isWorker() {
        return this == WORKER || this == SYSTEM_WORKER;
    }

    @JsonCreator
    public static ServiceType fromString(final String value) {
        try {
            return Enums.getForNameIgnoreCase(value, ServiceType.class, INVALID);
        } catch (IllegalArgumentException e) {
            return INVALID;
        }
    }
}
