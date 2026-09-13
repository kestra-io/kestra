package io.kestra.fethr.auth;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One row of the permission catalogue: an atomic permission with the display metadata the role matrix
 * needs (resource group, action badge, sort order). Projected from the {@link Permission} enum. The
 * friendly copy (headline/description) is not here; the SPA renders it from i18n keyed by {@code key}.
 */
@Introspected
@Schema(description = "A realm permission atom with its display metadata (one permission-catalogue row)")
public record PermissionVo(
    @Schema(description = "The entity.action permission key") String key,
    @Schema(description = "The entity the permission belongs to (the key prefix)") String resource,
    @Schema(description = "Action kind: read, write or destructive") Action action,
    @Schema(description = "Display order across the whole catalogue") int order) {
}
