package io.kestra.core.notification.model;

import io.kestra.core.server.CoreAsyncOperationType;

/**
 * The kind of operation {@link io.kestra.core.notification.NotificationService#notifyAsyncOperation} is notified about.
 * <p>
 * Implemented by {@link CoreAsyncOperationType} for the built-in operations, and by any other
 * producer's own enum (e.g. an EE feature's) — a plain Java enum cannot be extended across module
 * boundaries, so this interface is the seam that lets a producer outside this module contribute
 * its own values without this module knowing about them.
 */
public interface AsyncOperationType {
    /**
     * Every implementation is an enum, so {@link Enum#name()} satisfies this method with no override needed.
     */
    String name();

    /**
     * The kind of resource this operation type targets — an execution id or a trigger uid. A
     * backfill is addressed by the trigger uid it belongs to, hence {@code TRIGGER}.
     */
    ResourceType resourceType();

    enum ResourceType {
        EXECUTION,
        TRIGGER
    }
}
