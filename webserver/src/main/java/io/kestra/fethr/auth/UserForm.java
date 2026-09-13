package io.kestra.fethr.auth;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body to create a realm user. Per D1 (Anirban 2026-05-26) email invitations are deferred, so the
 * owner creates the user with an initial password directly. The role is the {@link Role} composite, validated
 * by {@link RoleCheck}; an unknown value deserializes to null (READ_UNKNOWN_ENUM_VALUES_AS_NULL) so the
 * validator owns the rejection rather than Jackson. Only an owner may assign the owner role, which the
 * controller enforces.
 */
@Introspected
@Schema(description = "Create a realm user with an initial password and a composite role")
public record UserForm(
    @NotBlank @Email @Schema(description = "Email; also used as the Keycloak username") String email,
    @NotBlank @Schema(description = "Initial password (the realm enforces the password policy)") String password,
    // Required: the realm's user profile mandates first/last name, and a user missing them cannot log in
    // (Keycloak triggers VERIFY_PROFILE -> "Account is not fully set up"). Fail at creation, not at first login.
    @NotBlank @Schema(description = "First name") String firstName,
    @NotBlank @Schema(description = "Last name") String lastName,
    @RoleCheck @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
    @Schema(description = "Composite role: owner, admin, member or viewer") Role role) {
}
