package io.kestra.core.worker;

import io.kestra.core.server.ServiceInstance;

/**
 * Shared constants for the Worker Group concept — the well-known service-property
 * key under which a worker publishes its resolved group id, and the default sentinel
 * id used when no specific group is resolved.
 */
public final class WorkerGroups {

    /** Default Worker Group id sentinel, used when no specific group is resolved. */
    public static final String DEFAULT_ID = "default";

    /** The {@link ServiceInstance#props()} key carrying the resolved Worker Group id. */
    public static final String SERVICE_PROPS_KEY = "worker.group.id";

    private WorkerGroups() {
    }

    /**
     * Normalizes a Worker Group id by mapping {@code null} or empty values to the
     * {@link #DEFAULT_ID} sentinel, so the rest of the system always sees a canonical
     * value.
     */
    public static String normalize(String workerGroupId) {
        return (workerGroupId == null || workerGroupId.isEmpty()) ? DEFAULT_ID : workerGroupId;
    }

    /**
     * Returns {@code true} if the given id refers to the {@link #DEFAULT_ID} sentinel,
     * including the absent ({@code null} or empty) cases that {@link #normalize(String)}
     * maps to it.
     */
    public static boolean isDefault(String workerGroupId) {
        return DEFAULT_ID.equals(normalize(workerGroupId));
    }
}
