package io.kestra.fethr.auth;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Introspected
@Schema(description = "Request body for the first-run onboarding sign-up endpoint.")
public record SignupForm(
    @NotBlank @Email
    @Schema(description = "Email of the owner user; also used as the Keycloak username.") String email,

    @NotBlank
    @Schema(description = "Initial password for the owner user.") String password,

    @Nullable
    @Schema(description = "Owner first name.") String firstName,

    @Nullable
    @Schema(description = "Owner last name.") String lastName,

    @Nullable
    @Schema(description = "Preferred timezone, IANA name.") String timezone,

    @Nullable
    @Schema(description = "Preferred locale.") String locale) {
}
