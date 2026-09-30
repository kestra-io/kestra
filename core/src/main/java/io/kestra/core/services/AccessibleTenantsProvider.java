package io.kestra.core.services;

import java.util.Set;

import io.kestra.core.tenant.TenantService;

import jakarta.inject.Singleton;

/**
 * Resolves the tenants a given user currently has access to.
 * <p>
 * OSS is single-tenant, so every user has access to {@link TenantService#MAIN_TENANT} only.
 * Extended and {@code @Replaces}d where access is computed from a real permission model (e.g. an
 * EE edition's RBAC tenant bindings).
 */
@Singleton
public class AccessibleTenantsProvider {
    public Set<String> accessibleTenantIds(String userId) {
        return Set.of(TenantService.MAIN_TENANT);
    }
}
