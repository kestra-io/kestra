package io.kestra.webserver.controllers.user;

import java.util.List;

import io.kestra.fethr.auth.Permission;
import io.kestra.fethr.auth.Role;
import io.kestra.fethr.auth.UserForm;
import io.kestra.fethr.auth.UserUpdateForm;
import io.kestra.fethr.auth.UserVo;
import io.kestra.fethr.auth.keycloak.KeycloakService;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.AuthorizationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;

/**
 * Owner screens — realm user management (SCRUM-467). Keycloak is the source of truth, so these endpoints
 * delegate to the admin client through {@link KeycloakService}. Each endpoint is gated on the atomic
 * {@code user.*} permission via {@code @Secured} (matched against the roles the {@code KeycloakRolesFinder}
 * puts into the Authentication); the four composite roles aggregate those atoms. Active only with Micronaut
 * Security on (the Keycloak deployment). Runs on the IO pool because the admin client is blocking and would
 * otherwise stall the Netty event loop.
 */
@Slf4j
@Controller("/api/v1/user")
@Requires(property = "micronaut.security.enabled", value = "true")
@ExecuteOn(TaskExecutors.IO)
@Tag(name = "User")
public class UserController {
    private final KeycloakService keycloak;

    @Inject
    public UserController(KeycloakService keycloak) {
        this.keycloak = keycloak;
    }

    @Get
    @Secured(Permission.Names.USER_READ)
    @Operation(summary = "List the realm users and their composite role.")
    public List<UserVo> list() {
        return keycloak.listUsers();
    }

    @Post
    @Secured(Permission.Names.USER_CREATE)
    @Operation(summary = "Create a realm user with an initial password and a composite role.")
    public HttpResponse<?> create(HttpRequest<?> request, Authentication authentication, @Valid @Body UserForm body) {
        // Only an owner can assign the owner role: becoming an owner is an ownership transfer
        // (organisation.transfer_ownership), not a normal member-management action (Yitzhak 2026-06-03).
        if (body.role() == Role.OWNER && !authentication.getRoles().contains(Role.OWNER.roleName())) {
            log.error("User '{}' attempted to assign the owner role without being an owner", authentication.getName());
            throw new AuthorizationException(authentication);
        }
        String userId = keycloak.createUser(body.email(), body.password(), body.firstName(), body.lastName(), body.role());
        return HttpResponse.created(UriBuilder.of(request.getPath()).path(userId).build());
    }

    @Delete("/{id}")
    @Secured(Permission.Names.USER_DELETE)
    @Operation(summary = "Remove a realm user.")
    public HttpResponse<?> remove(@PathVariable String id) {
        keycloak.removeUser(id);
        return HttpResponse.noContent();
    }

    @Post("/{id}/logout")
    @Secured(Permission.Names.USER_UPDATE)
    @Operation(summary = "Revoke all Keycloak sessions of a realm user (admin force-logout).")
    public HttpResponse<?> revokeSessions(@PathVariable String id) {
        keycloak.logout(id);
        return HttpResponse.noContent();
    }

    @Patch(value = "/{id}", consumes = "application/json-patch+json")
    @Secured(Permission.Names.USER_UPDATE)
    @Operation(summary = "Update a realm user (JSON merge patch: profile, enabled status and composite role).")
    public HttpResponse<?> update(Authentication authentication, @PathVariable String id, @Valid @Body UserUpdateForm body) {
        // Only an owner can assign the owner role: becoming an owner is an ownership transfer, not a normal
        // member-management action (Yitzhak 2026-06-03), the same guard as create.
        if (body.role() == Role.OWNER && !authentication.getRoles().contains(Role.OWNER.roleName())) {
            log.error("User '{}' attempted to assign the owner role without being an owner", authentication.getName());
            throw new AuthorizationException(authentication);
        }
        keycloak.updateUser(id, body);
        return HttpResponse.noContent();
    }

    @Put(value = "/{id}/reset-password", consumes = MediaType.TEXT_PLAIN)
    @Secured(Permission.Names.USER_UPDATE)
    @Operation(summary = "Reset a realm user's password to an admin-set temporary password (changed on next login).")
    public HttpResponse<?> resetPassword(@PathVariable String id, @Body @NotBlank String password) {
        keycloak.resetPassword(id, password);
        return HttpResponse.noContent();
    }

    // The 1.x fork overrode error handling here, turning the framework's 422 for an undecodable body
    // into a 400 carrying a JsonError, because its SPA only unwrapped a 400 to the body. 2.0 answers
    // every error as an RFC 9457 ProblemDetail through one global handler -- which already special-cases
    // a conversion failure to point at where in the document it happened. Re-adding a bespoke shape for
    // this one controller would emit a response nothing else in the instance emits, so the override goes
    // and an unknown role reports through the same contract as every other malformed body.
}
