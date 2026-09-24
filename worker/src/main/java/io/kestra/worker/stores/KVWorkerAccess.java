package io.kestra.worker.stores;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

/**
 * How a worker reaches the values of the KV store.
 */
public enum KVWorkerAccess {
    /** The worker reads and writes the values in the internal storage it is configured with. */
    STORAGE,
    /**
     * The worker reads and writes the entries through the controller, and holds no credentials for the
     * storage hosting the values.
     */
    CONTROLLER;

    public static final String CONFIG_KEY = "kestra.kv.worker-access";

    @JsonCreator
    public static KVWorkerAccess fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, KVWorkerAccess.class);
    }
}
