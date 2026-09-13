package io.kestra.webserver.controllers.role;

import java.util.List;

import io.kestra.fethr.auth.Permission;
import io.kestra.fethr.auth.RoleVo;
import io.kestra.fethr.auth.keycloak.KeycloakService;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;

/**
 * Owner screens — read-only view of the realm RBAC catalogue (SCRUM-467): the four composite roles and the
 * atomic permissions each grants, i.e. the permission matrix. Read-only because per D3 there are no custom
 * roles this year. Gated on {@code user.read}: the realm has no {@code role.*} atom and this is reference
 * data for user management (assigning roles), so it shares the users read permission. Runs on the IO pool
 * because the admin client is blocking; active only with Micronaut Security on (the Keycloak deployment).
 */
@Controller("/api/v1/role")
@Requires(property = "micronaut.security.enabled", value = "true")
@ExecuteOn(TaskExecutors.IO)
@Tag(name = "User")
public class RoleController {
    private final KeycloakService keycloak;

    @Inject
    public RoleController(KeycloakService keycloak) {
        this.keycloak = keycloak;
    }

    @Get
    @Secured(Permission.Names.USER_READ)
    @Operation(summary = "List the composite roles and the atomic permissions each grants (the RBAC matrix).")
    public List<RoleVo> list() {
        return keycloak.listRoles();
    }
}
