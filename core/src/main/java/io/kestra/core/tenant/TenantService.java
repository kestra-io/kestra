package io.kestra.core.tenant;

import java.util.List;

import jakarta.inject.Singleton;

@Singleton
public class TenantService {

    public static final String MAIN_TENANT = "main";

    /**
     * Resolve the current tenant and return its identifier.
     * If the tenant is the default tenant, it returns main, which is always the case on OSS as Tenant is an EE feature.
     *
     * @return the current tenant identifier
     */
    public String resolveTenant() {
        return MAIN_TENANT;
    }

    /**
     * List all tenants.
     * 
     * @return main on OSS as Tenant is an EE feature.
     */
    public List<String> listTenants() {
        return List.of(MAIN_TENANT);
    }
}
