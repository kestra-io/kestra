package io.kestra.core.tenant;

import jakarta.inject.Singleton;

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
}
