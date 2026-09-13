package io.kestra.fethr.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;

/**
 * Partial user-update body applied as a JSON Merge Patch (RFC 7386), mirroring the hl7-service edit-connector
 * pattern. Absent fields leave the current value unchanged; present fields override it. Presence detection
 * requires nullable fields plus {@link JsonInclude.Include#NON_NULL}, which is the sanctioned exception to the
 * "no null/optional" convention for a patch DTO. An unknown role value is rejected at deserialization (Jackson
 * throws rather than coercing it to null), which the user controller's error handler surfaces as a 400 with
 * the reason. Assigning the owner role and demoting/disabling the last owner are guarded server-side.
 */
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Partial user update (JSON merge patch); omitted fields are left unchanged")
public record UserUpdateForm(
    @Nullable @Schema(description = "First name") String firstName,
    @Nullable @Schema(description = "Last name") String lastName,
    @Nullable @Email @Schema(description = "Email; also updates the Keycloak username") String email,
    @Nullable @Schema(description = "Whether the user can log in") Boolean enabled,
    @Nullable @Schema(description = "Composite role: owner, admin, member or viewer") Role role) {
}
