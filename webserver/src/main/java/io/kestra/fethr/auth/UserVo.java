package io.kestra.fethr.auth;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A realm user (Keycloak) and the composite role assigned to them. Every managed user has one composite
 * (set at creation), so {@code role} is the {@link Role} enum directly.
 */
@Introspected
@Schema(description = "A realm user (Keycloak) and the composite role assigned to them")
public record UserVo(
    @Schema(description = "Keycloak user id") String id,
    @Schema(description = "Username (the email)") String username,
    @Schema(description = "Email") String email,
    @Schema(description = "First name") String firstName,
    @Schema(description = "Last name") String lastName,
    @Schema(description = "The assigned composite role") Role role,
    @Schema(description = "Whether the account is enabled") boolean enabled) {
}
