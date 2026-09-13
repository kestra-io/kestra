package io.kestra.webserver.controllers.permission;

import java.util.Arrays;
import java.util.List;

import io.kestra.fethr.auth.Permission;
import io.kestra.fethr.auth.PermissionVo;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;

/**
 * Owner screens — the realm permission catalogue (SCRUM-467): every atomic entity.action permission with
 * its resource, impact and display order, so the role matrix can group, badge and order permissions
 * without hard-coding any of that in the SPA. The {@link Permission} enum is the source of truth,
 * serialized to JSON here. Static data (no Keycloak call), gated on {@code user.read} like the role matrix
 * (it is reference data for user management); active only with Micronaut Security on (the Keycloak deployment).
 */
@Controller("/api/v1/permission")
@Requires(property = "micronaut.security.enabled", value = "true")
@Secured(Permission.Names.USER_READ)
@Tag(name = "User")
public class PermissionController {
    private final ConversionService conversionService;

    @Inject
    public PermissionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Get
    @Operation(summary = "List the realm permission catalogue (atoms with resource, impact and order).")
    public List<PermissionVo> list() {
        return Arrays.stream(Permission.values())
            .map(permission -> conversionService.convertRequired(permission, PermissionVo.class))
            .toList();
    }
}
