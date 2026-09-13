package io.kestra.webserver.controllers.audit;

import java.util.Arrays;
import java.util.List;

import io.kestra.fethr.auth.AuditAction;
import io.kestra.fethr.auth.AuditActionVo;
import io.kestra.fethr.auth.Role;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;

/**
 * The inventory of audited user-activity actions (SCRUM, 2026-06-18): every {@link AuditAction} with its
 * category, code and human descriptions, so the audit filter is built from the backend instead of hard-coding
 * the actions in the SPA. The {@link AuditAction} enum is the source of truth, serialized here through the
 * conversion service. Static data (no Keycloak call), gated to the owner/admin composite roles like the audit
 * log it feeds (audit is outside the RBAC atom catalogue); active only with Micronaut Security on.
 */
@Controller("/api/v1/audit/actions")
@Requires(property = "micronaut.security.enabled", value = "true")
@Secured({ Role.Names.OWNER, Role.Names.ADMIN })
@Tag(name = "Audit")
public class AuditActionController {
    private final ConversionService conversionService;

    @Inject
    public AuditActionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Get
    @Operation(summary = "List the inventory of audited user-activity actions (category, code, descriptions).")
    public List<AuditActionVo> list() {
        return Arrays.stream(AuditAction.values())
            .map(action -> conversionService.convertRequired(action, AuditActionVo.class))
            .toList();
    }
}
