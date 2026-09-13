package io.kestra.fethr.auth;

import java.util.List;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A composite role and the atomic {@code entity.action} permissions it aggregates (one matrix row).
 */
@Introspected
@Schema(description = "A composite role and the atomic entity.action permissions it aggregates (one matrix row)")
public record RoleVo(
    @Schema(description = "Composite role") Role name,
    @Schema(description = "Role description from the realm") String description,
    @Schema(description = "The atomic entity.action permissions this role grants") List<String> permissions) {
}
