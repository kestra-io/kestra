package io.kestra.controller.grpc.services;

import java.util.regex.Pattern;

import io.kestra.core.validations.validator.TenantIdValidator;

/**
 * Validates the tenant and the namespace a worker sends to address content in the internal storage, since they
 * become a storage path.
 */
final class StorageLocations {

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]*");

    private StorageLocations() {
    }

    static void validate(String tenantId, String namespace) {
        if (!TenantIdValidator.isValid(tenantId)) {
            throw new IllegalArgumentException("'%s' is not a valid tenant.".formatted(tenantId));
        }
        if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
            throw new IllegalArgumentException("'%s' is not a valid namespace.".formatted(namespace));
        }
    }
}
