package io.kestra.fethr.auth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.keycloak.representations.idm.AdminEventRepresentation;
import org.keycloak.representations.idm.EventRepresentation;

import io.kestra.fethr.auth.keycloak.KeycloakService;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Facade for the user-activity audit log. Retrieves the two Keycloak event sources concurrently (sign-ins from
 * the user event log, sign-out and password change from the admin event log, see {@link KeycloakService}) over
 * the date range, converts each through its individual {@link io.micronaut.core.convert.TypeConverter} bean,
 * aggregates and sorts most-recent first, resolves the username from the realm user map, and applies the
 * page-limit fallback. Filtering is by {@link AuditAction}. Runs only with Micronaut Security on; the caller
 * controller runs on the IO pool and the two fetches are dispatched in parallel on the IO executor.
 */
@Slf4j
@Singleton
public class AuditService {
    private static final Set<AuditAction> DEFAULT_ACTIONS = EnumSet.allOf(AuditAction.class);

    private final KeycloakService keycloak;
    private final ConversionService conversionService;
    private final ExecutorService ioExecutor;

    public AuditService(
        KeycloakService keycloak,
        ConversionService conversionService,
        @Named(TaskExecutors.IO) ExecutorService ioExecutor) {
        this.keycloak = keycloak;
        this.conversionService = conversionService;
        this.ioExecutor = ioExecutor;
    }

    /**
     * Retrieves a page of user activity events, newest first, as a plain list (the SelectTable infinite-scroll
     * pattern, like SecretsTable: the frontend pages over {@code page}/{@code size} and detects the end when a
     * page comes back empty). The stream is merged from two Keycloak sources with independent offsets, so a
     * single offset cannot be pushed down: the newest {@code (first+size)} are fetched from each, their union
     * holds the globally newest {@code (first+size)}, enough to return the {@code [first, first+size)} window.
     */
    public List<UserEventVo> retrieveUserEvents(List<AuditAction> actions, Long dateFrom, Long dateTo, int first, int size) {
        Set<AuditAction> effectiveActions = resolveActions(actions);
        boolean wantLogin = effectiveActions.contains(AuditAction.LOGIN);
        boolean wantAdmin = effectiveActions.contains(AuditAction.LOGOUT) || effectiveActions.contains(AuditAction.PASSWORD_CHANGED);
        Set<String> effectiveCodes = effectiveActions.stream().map(AuditAction::getCode).collect(Collectors.toSet());
        int fetch = first + size;

        CompletableFuture<List<EventRepresentation>> userEventsFuture = CompletableFuture.supplyAsync(
            () -> wantLogin ? keycloak.getEvents(dateFrom, dateTo, 0, fetch) : List.<EventRepresentation> of(), ioExecutor
        );
        CompletableFuture<List<AdminEventRepresentation>> adminEventsFuture = CompletableFuture.supplyAsync(
            () -> wantAdmin ? keycloak.getAdminEvents(dateFrom, dateTo, 0, fetch) : List.<AdminEventRepresentation> of(), ioExecutor
        );
        CompletableFuture<Map<String, String>> usernamesFuture = CompletableFuture.supplyAsync(
            keycloak::usernamesById, ioExecutor
        );

        try {
            CompletableFuture.allOf(userEventsFuture, adminEventsFuture, usernamesFuture).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Failed to retrieve the audit events", cause);
            throw new KestraSecurityException("Failed to retrieve the audit events", cause);
        }

        Map<String, String> usernamesById = usernamesFuture.join();
        List<UserEventVo> converted = new ArrayList<>();
        userEventsFuture.join().forEach(event -> conversionService.convert(event, UserEventVo.class).ifPresent(converted::add));
        adminEventsFuture.join().forEach(event -> conversionService.convert(event, UserEventVo.class).ifPresent(converted::add));

        List<UserEventVo> events = converted.stream()
            .filter(event -> effectiveCodes.contains(event.action()))
            .map(
                event -> new UserEventVo(
                    event.id(), event.category(), event.action(), event.shortDescription(), event.longDescription(),
                    event.timestamp(), event.userId(),
                    usernamesById.getOrDefault(event.userId(), event.username()), event.ipAddress()
                )
            )
            .sorted(Comparator.comparingLong(UserEventVo::timestamp).reversed())
            .toList();

        List<UserEventVo> page = first >= events.size()
            ? List.of()
            : events.subList(first, Math.min(first + size, events.size()));
        log.debug("Returning audit events [{}, {}) of {} fetched (actions={})", first, first + size, events.size(), effectiveCodes);
        return List.copyOf(page);
    }

    private Set<AuditAction> resolveActions(List<AuditAction> actions) {
        return actions == null || actions.isEmpty()
            ? DEFAULT_ACTIONS
            : EnumSet.copyOf(actions);
    }
}
