package io.kestra.core.worker;

import io.micronaut.core.annotation.Nullable;

public record MetadataChangePayload(Type type, String tenantId, @Nullable String namespace) {

    public enum Type {
        NAMESPACE,
        TENANT,
        FLOW
    }
}
