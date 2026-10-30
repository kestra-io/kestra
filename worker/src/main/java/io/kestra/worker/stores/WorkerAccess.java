package io.kestra.worker.stores;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

/**
 * How a worker reaches a store whose content lives in the internal storage, such as the KV store or the
 * namespace files.
 */
public enum WorkerAccess {
    /** The worker reads and writes the content in the internal storage it is configured with. */
    STORAGE,
    /**
     * The worker reads and writes the content through the controller, and holds no credentials for the
     * storage hosting it.
     */
    CONTROLLER;

    @JsonCreator
    public static WorkerAccess fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, WorkerAccess.class);
    }
}
