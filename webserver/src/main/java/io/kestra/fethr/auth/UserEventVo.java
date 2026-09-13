package io.kestra.fethr.auth;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A user activity event read from the Keycloak realm event log (the source of truth): sign in, sign out and
 * password change. The action is categorised in the backend (see {@link AuditAction}): {@code category} is the
 * domain ({@code user}), {@code action} is the stable machine code ({@code login}), and the short / long
 * descriptions are the human labels the SPA renders. {@code username} is resolved from the event details or the
 * realm users; it falls back to the user id when the user no longer exists.
 */
@Introspected
@Schema(description = "A user activity event from the Keycloak audit log")
public record UserEventVo(
    @Schema(description = "Keycloak event id") String id,
    @Schema(description = "Action category (e.g. user)") String category,
    @Schema(description = "Action code (e.g. login)") String action,
    @Schema(description = "Short human description (e.g. Signed in)") String shortDescription,
    @Schema(description = "Long human description (e.g. User signed in)") String longDescription,
    @Schema(description = "Event time (epoch milliseconds)") long timestamp,
    @Schema(description = "Keycloak user id") String userId,
    @Schema(description = "Username (the email)") String username,
    @Schema(description = "Originating IP address") String ipAddress) {
}
