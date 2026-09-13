package io.kestra.webserver.controllers.audit;

import java.util.List;

import io.kestra.fethr.auth.AuditAction;
import io.kestra.fethr.auth.AuditService;
import io.kestra.fethr.auth.Role;
import io.kestra.fethr.auth.UserEventVo;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;

/**
 * Audit log for user management (SCRUM, 2026-06-09): the user-activity view under Settings. Reads the
 * Keycloak realm event log (the source of truth) through {@link AuditService}, which merges the user and
 * admin event sources. Gated to the owner and admin composite roles only (Yitzhak 2026-06-09 "gate it for
 * admin"); audit is outside the RBAC atom catalogue, so the gate is the composite role name, not a
 * {@code *.read} atom. Active only with Micronaut Security on; runs on the IO pool because the admin client
 * is blocking.
 */
@Slf4j
@Controller("/api/v1/audit/user")
@Requires(property = "micronaut.security.enabled", value = "true")
@ExecuteOn(TaskExecutors.IO)
@Tag(name = "Audit")
public class AuditController {
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditService auditService;

    @Inject
    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @Get
    @Secured({ Role.Names.OWNER, Role.Names.ADMIN })
    @Operation(summary = "List a page of user activity events (sign in, sign out, password change) from the audit log, newest first.")
    public List<UserEventVo> getUserEvents(
        @Nullable @QueryValue List<AuditAction> actions,
        @Nullable @QueryValue Long dateFrom,
        @Nullable @QueryValue Long dateTo,
        @QueryValue(defaultValue = "1") @Min(1) int page,
        @QueryValue(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) int size) {
        // page/size mirror LogController.searchLogs; the size is clamped server-side so a client cannot
        // request an unbounded page. Returns a plain list (the page window): the SelectTable infinite-scroll
        // pages over page/size and detects the end when a page comes back empty (the SecretsTable pattern),
        // since the Keycloak events API exposes no total count. `actions` filters by AuditAction (each query
        // value is bound to the enum from its code by StringToAuditActionConverter).
        int boundedSize = Math.min(size, MAX_PAGE_SIZE);
        int first = (page - 1) * boundedSize;
        return auditService.retrieveUserEvents(actions, dateFrom, dateTo, first, boundedSize);
    }
}
