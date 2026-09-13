package io.kestra.fethr.auth.keycloak;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatchException;
import com.github.fge.jsonpatch.mergepatch.JsonMergePatch;

import io.kestra.core.exceptions.ConflictException;
import io.kestra.core.models.Setting;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.fethr.auth.KestraSecurityException;
import io.kestra.fethr.auth.OnboardingCompletedEvent;
import io.kestra.fethr.auth.Role;
import io.kestra.fethr.auth.RoleVo;
import io.kestra.fethr.auth.UserUpdateForm;
import io.kestra.fethr.auth.UserVo;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Context
@Singleton
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Slf4j
public class KeycloakService {
    public static final String KEYCLOAK_SETTINGS_KEY = "kestra.server.keycloak";
    private static final String OWNER_ROLE = "owner";

    @Inject
    private SettingRepositoryInterface settingRepository;

    @Inject
    private KeycloakConfiguration keycloakConfiguration;

    @Inject
    private BeanProvider<Keycloak> keycloakClient;

    @Inject
    private BeanProvider<KeycloakTokenClient> tokenClient;

    @Inject
    private ApplicationEventPublisher<OnboardingCompletedEvent> onboardingCompletedPublisher;

    public KeycloakService() {
    }

    @PostConstruct
    protected void init() {
        if (keycloakConfiguration == null || !keycloakConfiguration.isConfigured()) {
            return;
        }
        save(keycloakConfiguration);
    }

    public void save(KeycloakConfiguration configuration) {
        boolean unchanged = configuration().map(configuration::equals).orElse(false);
        if (!unchanged) {
            settingRepository.save(
                Setting.builder()
                    .key(KEYCLOAK_SETTINGS_KEY)
                    .value(configuration)
                    .build()
            );
        }
    }

    public Optional<KeycloakConfiguration> configuration() {
        return settingRepository.findByKey(KEYCLOAK_SETTINGS_KEY)
            .map(Setting::getValue)
            .map(value -> JacksonMapper.ofJson(false).convertValue(value, KeycloakConfiguration.class));
    }

    public String createOwner(String email, String password, String firstName, String lastName) {
        if (!keycloakClient.isPresent()) {
            log.error("Cannot create the owner '{}': Keycloak is not configured (base-url / realm)", email);
            throw new KestraSecurityException("Keycloak is not configured (kestra.server.keycloak.base-url / realm).");
        }
        Keycloak keycloak = keycloakClient.get();
        UsersResource users = keycloak.realm(keycloakConfiguration.realm()).users();
        String userId = createUserInRealm(users, email, firstName, lastName);
        provisionUser(keycloak, users, userId, password, OWNER_ROLE);
        onboardingCompletedPublisher.publishEvent(new OnboardingCompletedEvent(email, userId));
        return userId;
    }

    /**
     * Lists the realm users and the composite role assigned to each. Service accounts (the {@code kestra}
     * client's service-account user) are filtered out so only human users surface. The role is the
     * top-level assigned composite (owner/admin/member/viewer), not the expanded atoms.
     */
    public List<UserVo> listUsers() {
        RealmResource realm = requireRealm();
        return realm.users().list().stream()
            .filter(user -> user.getServiceAccountClientId() == null)
            .map(user -> toUser(realm, user))
            // Only workspace members surface: identities that carry realm permission atoms but no
            // composite role (owner/admin/member/viewer) are not members.
            .filter(user -> user.role() != null)
            .toList();
    }

    /**
     * Creates a realm user with an initial (non-temporary) password and assigns one of the assignable
     * composite roles. Mirrors {@link #createOwner} but the role is the caller's choice; assigning the owner
     * role is allowed only for an owner actor, which the controller enforces. Throws {@link ConflictException}
     * when the email already exists.
     */
    public String createUser(String email, String password, String firstName, String lastName, Role role) {
        RealmResource realm = requireRealm();
        UsersResource users = realm.users();
        String userId = createUserInRealm(users, email, firstName, lastName);
        provisionUser(keycloakClient.get(), users, userId, password, role.roleName());
        return userId;
    }

    /**
     * Removes a realm user (deletes the Keycloak user). The realm in turn ends the user's sessions.
     */
    public void removeUser(String userId) {
        RealmResource realm = requireRealm();
        if (isLastOwner(realm, userId)) {
            log.error("Refused to remove the last owner '{}'", userId);
            throw new ConflictException("Cannot remove the last owner of the workspace");
        }
        try (Response response = realm.users().delete(userId)) {
            int status = response.getStatus();
            if (status == Response.Status.NOT_FOUND.getStatusCode()) {
                log.error("Cannot remove user '{}': not found", userId);
                throw new ConflictException("User not found");
            }
            if (status >= Response.Status.BAD_REQUEST.getStatusCode()) {
                log.error("Keycloak rejected removing user '{}': HTTP {}", userId, status);
                throw new KestraSecurityException("Keycloak rejected removing the user: HTTP " + status);
            }
        } catch (WebApplicationException e) {
            log.error("Keycloak user removal failed for {}", userId, e);
            throw new KestraSecurityException("Failed to remove the user", e);
        }
    }

    /**
     * Applies a partial update to a realm user as a JSON Merge Patch (RFC 7386), mirroring the hl7-service
     * edit-connector pattern: the partial form is merged over the current user, then the merged profile/enabled
     * goes through a single {@code update} and the composite role is re-mapped only when it changed. Disabling
     * or demoting the last owner is refused with {@link ConflictException}; assigning the owner role is
     * owner-only and enforced by the controller.
     */
    public void updateUser(String userId, UserUpdateForm patch) {
        RealmResource realm = requireRealm();
        try {
            UserRepresentation current = realm.users().get(userId).toRepresentation();
            UserVo currentVo = toUser(realm, current);
            UserVo merged = applyMergePatch(currentVo, patch);

            if (!merged.enabled() && isLastOwner(realm, userId)) {
                log.error("Refused to disable the last owner '{}'", userId);
                throw new ConflictException("Cannot disable the last owner of the workspace");
            }
            boolean demotingOwner = currentVo.role() == Role.OWNER && merged.role() != Role.OWNER;
            if (demotingOwner && isLastOwner(realm, userId)) {
                log.error("Refused to demote the last owner '{}'", userId);
                throw new ConflictException("Cannot demote the last owner of the workspace");
            }

            current.setFirstName(merged.firstName());
            current.setLastName(merged.lastName());
            current.setEmail(merged.email());
            current.setEnabled(merged.enabled());
            realm.users().get(userId).update(current);

            if (merged.role() != currentVo.role()) {
                changeRole(realm, userId, currentVo.role(), merged.role());
            }
        } catch (WebApplicationException e) {
            throw keycloakClientError(e, "Failed to update the user", "user update for " + userId);
        }
    }

    /**
     * Resets a realm user's password to an admin-set temporary password (SCRUM-506 / unifies SCRUM-471). The
     * credential is marked temporary, so the realm forces the user to change it on the next login.
     */
    public void resetPassword(String userId, String tempPassword) {
        RealmResource realm = requireRealm();
        try {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(tempPassword);
            credential.setTemporary(true);
            realm.users().get(userId).resetPassword(credential);
        } catch (WebApplicationException e) {
            throw keycloakClientError(e, "Failed to reset the user password", "password reset for " + userId);
        }
    }

    /**
     * Lists the four composite roles (the RBAC catalogue) and the atomic {@code entity.action} permissions
     * each aggregates, read straight from the realm (the source of truth) so the owner-screens permission
     * matrix never drifts from Keycloak. Read-only: per D3 there are no custom roles. The composites carry
     * their atoms directly (verified against the realm), so the composite members are exactly the atoms.
     */
    public List<RoleVo> listRoles() {
        RealmResource realm = requireRealm();
        try {
            return Arrays.stream(Role.values())
                .map(role ->
                {
                    RoleResource roleResource = realm.roles().get(role.roleName());
                    List<String> permissions = roleResource.getRealmRoleComposites().stream()
                        .map(RoleRepresentation::getName)
                        .filter(name -> name.contains("."))
                        .sorted()
                        .toList();
                    return new RoleVo(role, StringUtils.defaultString(roleResource.toRepresentation().getDescription()), permissions);
                })
                .toList();
        } catch (WebApplicationException e) {
            log.error("Keycloak roles listing failed", e);
            throw new KestraSecurityException("Failed to list the realm roles", e);
        }
    }

    /**
     * fethr: the effective realm roles the user (the Keycloak {@code sub}) holds — the {@code entity.action}
     * permission atoms, with composite roles expanded via {@code listEffective()} — read straight from the
     * realm through the existing admin client. Used by {@code KeycloakAuthorizationAdapter} to authorize the
     * passed subject rather than the ambient thread principal (so it is correct off the request thread too).
     * Filtered to the dotted atoms (the permission-key shape), mirroring {@link #listRoles()}. Read-only.
     */
    public Set<String> realmRolesForUser(String userId) {
        RealmResource realm = requireRealm();
        try {
            return realm.users().get(userId).roles().realmLevel().listEffective().stream()
                .map(RoleRepresentation::getName)
                .filter(name -> name.contains("."))
                .collect(Collectors.toUnmodifiableSet());
        } catch (WebApplicationException e) {
            // Type only: keeps the subject and any admin-client detail out of the log.
            log.error("Keycloak role lookup failed for a user: {}", e.getClass().getSimpleName());
            throw new KestraSecurityException("Failed to read the user roles", e);
        }
    }

    /**
     * The newest realm user events of type {@code LOGIN} (sign-ins) in the date range, as raw Keycloak
     * representations; {@link AuditService} converts and merges them. {@code dateFrom}/{@code dateTo} are
     * epoch milliseconds (the admin client's time-precise events overload); a null bound is open-ended (from
     * the epoch, or up to now). {@code first}/{@code max} page the source.
     */
    public List<EventRepresentation> getEvents(Long dateFrom, Long dateTo, int first, int max) {
        RealmResource realm = requireRealm();
        long from = dateFrom != null ? dateFrom : 0L;
        long to = dateTo != null ? dateTo : System.currentTimeMillis();
        try {
            // "LOGIN" is the Keycloak user-event type (not our AuditAction taxonomy); sign-out and password
            // change are admin events, fetched separately in getAdminEvents.
            return realm.getEvents(List.of("LOGIN"), null, null, from, to, null, first, max, null);
        } catch (WebApplicationException e) {
            log.error("Keycloak user events listing failed", e);
            throw new KestraSecurityException("Failed to list the user events", e);
        }
    }

    /**
     * The newest realm admin events on the {@code USER} resource in the date range, as raw Keycloak
     * representations; sign-out and password reset go through the Admin API, so they live here, not in the
     * user event log. {@link AuditService} classifies them by resource path, converts and merges them.
     * {@code dateFrom}/{@code dateTo} are epoch milliseconds; a null bound is open-ended.
     */
    public List<AdminEventRepresentation> getAdminEvents(Long dateFrom, Long dateTo, int first, int max) {
        RealmResource realm = requireRealm();
        long from = dateFrom != null ? dateFrom : 0L;
        long to = dateTo != null ? dateTo : System.currentTimeMillis();
        try {
            return realm.getAdminEvents(null, null, null, null, null, null, List.of("USER"), from, to, first, max, null);
        } catch (WebApplicationException e) {
            log.error("Keycloak admin events listing failed", e);
            throw new KestraSecurityException("Failed to list the admin events", e);
        }
    }

    /**
     * A one-shot map of realm user id to username (the email), excluding service accounts, so the audit
     * service can resolve the subject of an event (admin events only carry the user id in the resource path).
     */
    public Map<String, String> usernamesById() {
        RealmResource realm = requireRealm();
        try {
            return realm.users().list().stream()
                .filter(user -> user.getServiceAccountClientId() == null)
                .collect(Collectors.toMap(UserRepresentation::getId, UserRepresentation::getUsername, (a, b) -> a));
        } catch (WebApplicationException e) {
            log.error("Keycloak users listing failed", e);
            throw new KestraSecurityException("Failed to list the realm users", e);
        }
    }

    private UserVo toUser(RealmResource realm, UserRepresentation user) {
        Role role = realm.users().get(user.getId()).roles().realmLevel().listAll().stream()
            .map(RoleRepresentation::getName)
            .map(Role::fromRoleName)
            .flatMap(Optional::stream)
            .findFirst()
            .orElse(null);
        return new UserVo(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            StringUtils.defaultString(user.getFirstName()),
            StringUtils.defaultString(user.getLastName()),
            role,
            Boolean.TRUE.equals(user.isEnabled())
        );
    }

    /**
     * True when {@code userId} currently holds the owner composite AND is the only owner in the realm, so
     * removing them would leave the workspace with no owner. Counted via the owner role's direct user
     * members (no per-user fan-out).
     */
    private boolean isLastOwner(RealmResource realm, String userId) {
        try {
            // Only need to tell "exactly one owner" from "more than one"; two is enough and avoids
            // paging the whole owner list.
            List<UserRepresentation> owners = realm.roles().get(OWNER_ROLE).getUserMembers(0, 2);
            return owners.size() <= 1 && owners.stream().anyMatch(owner -> userId.equals(owner.getId()));
        } catch (WebApplicationException e) {
            log.error("Failed to check the owner count while guarding user '{}'", userId, e);
            throw new KestraSecurityException("Failed to verify the owner count", e);
        }
    }

    private UserVo applyMergePatch(UserVo current, UserUpdateForm patch) {
        try {
            ObjectMapper mapper = JacksonMapper.ofJson();
            JsonNode merged = JsonMergePatch.fromJson(mapper.valueToTree(patch))
                .apply(mapper.valueToTree(current));
            return mapper.treeToValue(merged, UserVo.class);
        } catch (JsonPatchException | JsonProcessingException e) {
            log.error("Failed to apply the user update patch", e);
            throw new KestraSecurityException("Failed to apply the user update", e);
        }
    }

    /**
     * Re-maps a user's composite role: removes the current composite and adds the new one. The last-owner
     * guard is applied by {@link #updateUser} before any write.
     */
    private void changeRole(RealmResource realm, String userId, Role current, Role next) {
        if (current != null) {
            RoleRepresentation old = realm.roles().get(current.roleName()).toRepresentation();
            realm.users().get(userId).roles().realmLevel().remove(List.of(old));
        }
        if (next != null) {
            RoleRepresentation added = realm.roles().get(next.roleName()).toRepresentation();
            realm.users().get(userId).roles().realmLevel().add(List.of(added));
        }
    }

    private RealmResource requireRealm() {
        if (!keycloakClient.isPresent()) {
            log.error("Cannot perform the user operation: Keycloak is not configured (base-url / realm)");
            throw new KestraSecurityException("Keycloak is not configured (kestra.server.keycloak.base-url / realm).");
        }
        return keycloakClient.get().realm(keycloakConfiguration.realm());
    }

    /**
     * Maps a Keycloak admin-client failure to the right status. A 400 from Keycloak is a client error
     * (most often a password-policy violation or an invalid field), so it is surfaced as a 400 carrying
     * Keycloak's own message ("must contain ... upper case ...") instead of a misleading 500; anything
     * else is an unexpected server failure. The 400 lets the UI show that message: the axios error
     * interceptor unwraps a 400 to its response body, which the dialogs read. Mirrors the honest-error
     * fix from SCRUM-510.
     */
    private RuntimeException keycloakClientError(WebApplicationException e, String serverFailureMessage, String context) {
        Response response = e.getResponse();
        if (response != null && response.getStatus() == Response.Status.BAD_REQUEST.getStatusCode()) {
            String detail = readKeycloakErrorMessage(response).orElse("the value is invalid");
            log.info("Keycloak rejected the request ({}): {}", context, detail);
            return new HttpStatusException(HttpStatus.BAD_REQUEST, detail);
        }
        log.error("Keycloak request failed ({})", context, e);
        return new KestraSecurityException(serverFailureMessage, e);
    }

    /** Best-effort extraction of Keycloak's error message ({@code errorMessage}/{@code error_description}/{@code error}). */
    private Optional<String> readKeycloakErrorMessage(Response response) {
        try {
            if (response.hasEntity()) {
                response.bufferEntity();
                String body = response.readEntity(String.class);
                if (!StringUtils.isBlank(body)) {
                    JsonNode node = JacksonMapper.ofJson().readTree(body);
                    for (String field : List.of("errorMessage", "error_description", "error")) {
                        JsonNode value = node.get(field);
                        if (value != null && !value.asText().isBlank()) {
                            return Optional.of(value.asText());
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Could not read the Keycloak error message from the response", ex);
        }
        return Optional.empty();
    }

    /**
     * Authenticates a user against Keycloak via Direct Grant (password grant) and returns the
     * token response (access + refresh JWT). Empty when the credentials are rejected; the realm
     * enforces the password policy and brute-force protection. Direct Grant is the design decision
     * recorded in {@code docs/diagrams/kestra-ciba-direct-grant.puml}.
     */
    public Optional<AccessTokenResponse> signIn(String username, String password) {
        if (keycloakConfiguration == null || !keycloakConfiguration.isConfigured() || !tokenClient.isPresent()) {
            log.error("Cannot sign in '{}': Keycloak is not configured (base-url / realm)", username);
            throw new KestraSecurityException("Keycloak is not configured (kestra.server.keycloak.base-url / realm).");
        }
        try {
            return Optional.of(
                tokenClient.get().token(
                    java.util.Map.of(
                        "grant_type", "password",
                        "client_id", keycloakConfiguration.clientId(),
                        "client_secret", keycloakConfiguration.clientSecret(),
                        "username", username,
                        "password", password,
                        "scope", "openid"
                    )
                )
            );
        } catch (HttpClientResponseException e) {
            int status = e.getStatus().getCode();
            if (
                status == Response.Status.UNAUTHORIZED.getStatusCode()
                    || status == Response.Status.BAD_REQUEST.getStatusCode()
            ) {
                log.info("Sign-in rejected for '{}': invalid credentials (HTTP {})", username, status);
                return Optional.empty();
            }
            log.error("Keycloak sign-in failed for '{}'", username, e);
            throw new KestraSecurityException("Keycloak sign-in failed", e);
        }
    }

    /**
     * Exchanges a refresh token for a fresh access/refresh token pair via the realm token endpoint
     * ({@code grant_type=refresh_token}). Empty when the refresh token is expired or revoked.
     */
    public Optional<AccessTokenResponse> refresh(String refreshToken) {
        if (keycloakConfiguration == null || !keycloakConfiguration.isConfigured() || !tokenClient.isPresent()) {
            log.error("Cannot refresh token: Keycloak is not configured (base-url / realm)");
            throw new KestraSecurityException("Keycloak is not configured (kestra.server.keycloak.base-url / realm).");
        }
        try {
            return Optional.of(
                tokenClient.get().token(
                    java.util.Map.of(
                        "grant_type", "refresh_token",
                        "client_id", keycloakConfiguration.clientId(),
                        "client_secret", keycloakConfiguration.clientSecret(),
                        "refresh_token", refreshToken
                    )
                )
            );
        } catch (HttpClientResponseException e) {
            int status = e.getStatus().getCode();
            if (
                status == Response.Status.UNAUTHORIZED.getStatusCode()
                    || status == Response.Status.BAD_REQUEST.getStatusCode()
            ) {
                log.info("Token refresh rejected (HTTP {})", status);
                return Optional.empty();
            }
            log.error("Keycloak token refresh failed (HTTP {})", status, e);
            throw new KestraSecurityException("Keycloak token refresh failed", e);
        }
    }

    /**
     * Ends all Keycloak sessions for the user (the realm owns session management). The caller
     * resolves {@code userId} from the {@code sub} claim of the authenticated token.
     */
    public void logout(String userId) {
        if (!keycloakClient.isPresent()) {
            log.error("Cannot log out user '{}': Keycloak is not configured (base-url / realm)", userId);
            throw new KestraSecurityException("Keycloak is not configured (kestra.server.keycloak.base-url / realm).");
        }
        try {
            keycloakClient.get().realm(keycloakConfiguration.realm()).users().get(userId).logout();
        } catch (WebApplicationException e) {
            log.error("Keycloak logout failed for user {}", userId, e);
            throw new KestraSecurityException("Keycloak logout failed", e);
        }
    }

    private String createUserInRealm(UsersResource users, String email, String firstName, String lastName) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(email);
        user.setEmail(email);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setFirstName(StringUtils.defaultString(firstName));
        user.setLastName(StringUtils.defaultString(lastName));

        try (Response response = users.create(user)) {
            int status = response.getStatus();
            if (status == Response.Status.CONFLICT.getStatusCode()) {
                log.error("Cannot create user '{}': a user with that email already exists", email);
                throw new ConflictException("A user with that email already exists");
            }
            if (status >= Response.Status.BAD_REQUEST.getStatusCode()) {
                log.error("Keycloak rejected the user creation for '{}': HTTP {}", email, status);
                throw new KestraSecurityException("Keycloak rejected the user creation: HTTP " + status);
            }
            String location = String.valueOf(response.getLocation());
            int idx = location.lastIndexOf('/');
            if (idx < 0 || idx == location.length() - 1) {
                log.error("Keycloak did not return the created user id for '{}' (location='{}')", email, location);
                throw new KestraSecurityException("Keycloak did not return the created user id");
            }
            return location.substring(idx + 1);
        } catch (WebApplicationException e) {
            log.error("Keycloak user creation failed for {}", email, e);
            throw new KestraSecurityException("Failed to create the Keycloak user", e);
        }
    }

    private void provisionUser(Keycloak keycloak, UsersResource users, String userId, String password, String roleName) {
        try {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);
            users.get(userId).resetPassword(credential);

            RoleRepresentation role = keycloak.realm(keycloakConfiguration.realm())
                .roles().get(roleName).toRepresentation();
            users.get(userId).roles().realmLevel().add(List.of(role));
        } catch (WebApplicationException e) {
            log.error("Keycloak provisioning (role '{}') failed for user {}", roleName, userId, e);
            throw new KestraSecurityException("Failed to provision the Keycloak user", e);
        }
    }

    @ConfigurationProperties("kestra.server.keycloak")
    public record KeycloakConfiguration(
        @Bindable(defaultValue = "") String baseUrl,
        @Bindable(defaultValue = "") String realm,
        @Bindable(defaultValue = "") String clientId,
        @Bindable(defaultValue = "") String clientSecret) {
        public boolean isConfigured() {
            return !baseUrl.isBlank() && !realm.isBlank();
        }
    }
}
