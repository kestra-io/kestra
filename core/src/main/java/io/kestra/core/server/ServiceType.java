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
    INVALID;
    
    @JsonCreator
    public static ServiceType fromString(final String value) {
        try {
            return Enums.getForNameIgnoreCase(value, ServiceType.class, INVALID);
        } catch (IllegalArgumentException e) {
            return INVALID;
        }
    }
}
