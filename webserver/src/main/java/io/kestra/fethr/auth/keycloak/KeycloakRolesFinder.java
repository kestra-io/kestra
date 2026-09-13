package io.kestra.fethr.auth.keycloak;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.security.token.RolesFinder;
import jakarta.inject.Singleton;

/**
 * Resolves authorities from a Keycloak access token. Keycloak nests realm roles under
 * {@code realm_access.roles}, not a flat top-level claim, so the stock RolesFinder cannot read
 * them. These realm roles are the RBAC source of truth (the realm's {@code entity.action}
 * permission roles plus the owner/admin/member/viewer composites).
 */
@Singleton
@Replaces(RolesFinder.class)
@Requires(property = "micronaut.security.enabled", value = "true")
public class KeycloakRolesFinder implements RolesFinder {

    @Override
    public List<String> resolveRoles(@Nullable Map<String, Object> attributes) {
        List<String> roles = new ArrayList<>();
        if (attributes == null) {
            return roles;
        }
        if (
            attributes.get("realm_access") instanceof Map<?, ?> realmAccess
                && realmAccess.get("roles") instanceof Collection<?> realmRoles
        ) {
            for (Object role : realmRoles) {
                if (role != null) {
                    roles.add(role.toString());
                }
            }
        }
        return roles;
    }
}
