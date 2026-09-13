package io.kestra.fethr.auth;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row of the audited-action inventory: an {@link AuditAction} with its category, stable machine code and
 * the short / long human descriptions, so the frontend builds the audit filter from the backend instead of
 * hard-coding the actions. Projected from the {@link AuditAction} enum.
 */
@Introspected
@Schema(description = "An audited user-activity action with its category and descriptions (one audit-action inventory row)")
public record AuditActionVo(
    @Schema(description = "Action category (e.g. user)") String category,
    @Schema(description = "Stable machine action code (e.g. login)") String code,
    @Schema(description = "Short human description (e.g. Signed in)") String shortDescription,
    @Schema(description = "Long human description (e.g. User signed in)") String longDescription) {
}
