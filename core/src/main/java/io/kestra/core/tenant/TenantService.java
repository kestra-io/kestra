package io.kestra.core.tenant;

import io.kestra.core.storages.StorageConfiguration;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
public class TenantService {

    /**
     * Resolve the current tenant and return its identifier.
     * If the tenant is the default tenant, it returns null, which is always the case on OSS as Tenant is an EE feature.
     *
     * @return the current tenant identifier
     */
    public String resolveTenant() {
        return null;
    }

    /**
     * Load the Internal Storage configuration for the given tenant identifier if there is any configured.
     * As tenant is an EE feature, it always returns an empty Optional on OSS.
     */
    public Optional<StorageConfiguration> storageConfiguration(String tenantId) {
        return Optional.empty();
    }
}
