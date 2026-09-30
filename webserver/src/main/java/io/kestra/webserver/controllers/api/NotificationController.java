package io.kestra.webserver.controllers.api;

import java.time.Instant;
import java.util.List;

import io.kestra.core.exceptions.InvalidException;
import io.kestra.core.models.notifications.Notification;
import io.kestra.core.repositories.NotificationRepositoryInterface;
import io.kestra.core.repositories.NotificationRepositoryInterface.NotificationCursor;
import io.kestra.core.services.NotificationService;
import io.kestra.core.services.NotificationService.FollowSubscription;
import io.kestra.webserver.services.SseConnectionMetrics;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.sse.Event;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import lombok.Builder;
import reactor.core.publisher.Flux;

/**
 * Notifications are user-global (see class Javadoc on {@link NotificationRepositoryInterface}):
 * every endpoint resolves the current user via {@link #resolveCurrentUserId()} ({@code null} for
 * OSS's single implicit user), and {@link NotificationService} resolves their accessible tenants
 * itself, rather than a single path/query {@code tenantId} as most other controllers do.
 */
@Controller("/api/v1/notifications")
public class NotificationController {

    @Inject
    private NotificationService notificationService;

    @Inject
    private SseConnectionMetrics sseConnectionMetrics;

    @Get(uri = "/since")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Notifications" }, summary = "Poll for notifications updated since a given instant",
        description = "Interval-polling delta endpoint: returns notifications updated strictly after `since`, plus a fresh server timestamp to pass as `since` on the next poll."
    )
    public ApiNotificationsSince pollSince(
        @Parameter(description = "The server timestamp returned by a previous call to this endpoint (or to /history)") @QueryValue Instant since) {
        String userId = resolveCurrentUserId();

        // Captured before the query: a row committed mid-query has updatedDate <= serverTime and
        // must still be visible on the *next* poll (> serverTime), never silently dropped. The
        // client dedups by id, so re-returning a row already seen is harmless; missing one is not.
        Instant serverTime = Instant.now();
        List<Notification> notifications = notificationService.findByUserSince(userId, since);
        return ApiNotificationsSince.builder()
            .notifications(notifications)
            .serverTime(serverTime)
            .build();
    }

    @Get(uri = "/follow", produces = MediaType.TEXT_EVENT_STREAM)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Notifications" }, summary = "Follow live notification updates for the authenticated user",
        description = "Server-Sent Events stream: pushes every notification created or updated for the current user, restricted to their currently accessible tenants. The SSE event id is `created` or `updated`."
    )
    public Flux<Event<Notification>> listenUserNotifications() {
        FollowSubscription subscription = notificationService.follow(resolveCurrentUserId());
        return sseConnectionMetrics.track(subscription.flux(), "notification", subscription.unregister());
    }

    @Get(uri = "/history")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Notifications" }, summary = "Browse notification history",
        description = "Cursor-based (not offset-based): returns up to `limit` notifications older than `before`, most recent first, plus a `nextCursor` for the following page. Offset pagination is unsafe here because rows are purged on a rolling TTL while the user scrolls."
    )
    public ApiNotificationHistory history(
        @Parameter(description = "Cursor of the previous page, as `<createdDate>,<id>`. Omit for the first page.") @QueryValue @Nullable String before,
        @Parameter(description = "Maximum number of notifications to return") @QueryValue(defaultValue = "20") int limit) {
        String userId = resolveCurrentUserId();

        // Same before-query capture as pollSince(): keep the two serverTime semantics aligned so
        // a client that switches from history to since-polling doesn't gain or lose a gap.
        Instant serverTime = Instant.now();
        List<Notification> notifications = notificationService.findByUser(userId, parseCursor(before), limit);
        String nextCursor = notifications.size() < limit || notifications.isEmpty()
            ? null
            : toCursor(notifications.get(notifications.size() - 1));

        return ApiNotificationHistory.builder()
            .notifications(notifications)
            .serverTime(serverTime)
            .nextCursor(nextCursor)
            .build();
    }

    @Get(uri = "/unread-count")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Notifications" }, summary = "Count unread notifications for the authenticated user")
    public long unreadCount() {
        return notificationService.countUnread(resolveCurrentUserId());
    }

    @Post(uri = "/{id}/read")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Notifications" }, summary = "Mark a single notification as read",
        description = "Idempotent: a missing/already-read/foreign id is a no-op, not an error — the frontend never needs to special-case a 'not found' response for this action, and the global 404 handler never gets a chance to blow up the routed UI over what is functionally a success."
    )
    public HttpResponse<Void> markRead(@PathVariable String id) {
        notificationService.markRead(resolveCurrentUserId(), id);
        return HttpResponse.noContent();
    }

    @Post(uri = "/{id}/unread")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Notifications" }, summary = "Mark a single notification as unread",
        description = "Idempotent: a missing/already-unread/foreign id is a no-op, not an error — same rationale as POST /{id}/read."
    )
    public HttpResponse<Void> markUnread(@PathVariable String id) {
        notificationService.markUnread(resolveCurrentUserId(), id);
        return HttpResponse.noContent();
    }

    @Post(uri = "/read-all")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Notifications" }, summary = "Mark all of the authenticated user's notifications as read",
        description = "Only affects notifications for tenants the user currently has access to."
    )
    public ApiMarkAllRead markAllRead() {
        return new ApiMarkAllRead(notificationService.markAllRead(resolveCurrentUserId()));
    }

    /**
     * The authenticated user's id, or {@code null} when OSS has no real user model. Overridden in EE.
     */
    @Nullable
    protected String resolveCurrentUserId() {
        return null;
    }

    @Nullable
    private NotificationCursor parseCursor(@Nullable String before) {
        if (before == null || before.isBlank()) {
            return null;
        }

        String[] parts = before.split(",", 2);
        if (parts.length != 2) {
            throw invalidCursor(before);
        }

        try {
            return new NotificationCursor(Instant.parse(parts[0]), parts[1]);
        } catch (Exception e) {
            throw invalidCursor(before);
        }
    }

    private InvalidException invalidCursor(String before) {
        return new InvalidException(before, "Invalid cursor, expected '<createdDate>,<id>'");
    }

    private static String toCursor(Notification notification) {
        return notification.getCreatedDate() + "," + notification.getId();
    }

    @Serdeable
    @Builder
    public record ApiNotificationsSince(List<Notification> notifications, Instant serverTime) {
    }

    @Serdeable
    @Builder
    public record ApiNotificationHistory(List<Notification> notifications, Instant serverTime, @Nullable String nextCursor) {
    }

    @Serdeable
    public record ApiMarkAllRead(int updated) {
    }
}
