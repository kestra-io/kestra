package io.kestra.fethr.auth;

import java.util.List;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

@Introspected
@Schema(description = "The authenticated user's identity and Keycloak realm roles")
public record MeResponse(
    @Schema(description = "The authenticated user's username (email)") String username,
    @Schema(description = "The user's Keycloak realm roles (entity.action permissions plus the composite role)") List<String> roles) {
}
