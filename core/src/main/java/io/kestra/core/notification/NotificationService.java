package io.kestra.core.notification;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.kestra.core.notification.NotificationRepositoryInterface.NotificationCursor;
import io.kestra.core.notification.model.AsyncOperationType;
import io.kestra.core.notification.model.CoreNotificationType;
import io.kestra.core.notification.model.Notification;
import io.kestra.core.notification.model.NotificationEvent;
import io.kestra.core.notification.model.NotificationEventType;
import io.kestra.core.notification.model.NotificationItem;
import io.kestra.core.notification.model.NotificationItemOutcome;
import io.kestra.core.notification.model.NotificationType;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;

import io.micronaut.http.sse.Event;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

/**
 * Write-path facade for {@link Notification}s. This is the seam producers (an EE case-management
 * feature, async-operation progress, ...) target: it writes synchronously to the repository today,
 * but callers never see the persistence mechanism, so the write path can move behind a durable
 * queue later with zero caller changes.
 * <p>
 * Every create/update also emits a {@link NotificationEvent} on {@link #notificationQueue}, so
 * consumers (e.g. a live UI bell) don't have to poll — {@link #follow} is the read-path
 * counterpart, building the SSE stream on top of {@link NotificationStreamingService}.
 * <p>
 * Retention is flat and identical for every {@link NotificationType} (see {@link #purge()}) — read
 * notifications are purged after 7 days, unread after 30 days.
 */
@Slf4j
@Singleton
public class NotificationService {

    private static final int READ_RETENTION_DAYS = 7;
    private static final int UNREAD_RETENTION_DAYS = 30;
    private static final Duration NOTIFICATION_SAMPLE_INTERVAL = Duration.ofMillis(250);

    private final NotificationRepositoryInterface notificationRepository;
    private final NotificationItemRepositoryInterface notificationItemRepository;
    private final BroadcastQueueInterface<NotificationEvent> notificationQueue;
    private final NotificationStreamingService notificationStreamingService;

    @Inject
    public NotificationService(
        NotificationRepositoryInterface notificationRepository,
        NotificationItemRepositoryInterface notificationItemRepository,
        BroadcastQueueInterface<NotificationEvent> notificationQueue,
        NotificationStreamingService notificationStreamingService) {
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository must not be null");
        this.notificationItemRepository = Objects.requireNonNull(notificationItemRepository, "notificationItemRepository must not be null");
        this.notificationQueue = Objects.requireNonNull(notificationQueue, "notificationQueue must not be null");
        this.notificationStreamingService = Objects.requireNonNull(notificationStreamingService, "notificationStreamingService must not be null");
    }

    /**
     * Creates a new, unread notification. {@code userId} is {@code null} for OSS's single implicit
     * user (no real user model).
     */
    public Notification notify(@Nullable String userId, @Nullable String tenantId, NotificationType type, String title, @Nullable String referenceId) {
        Notification created = createNotification(userId, tenantId, type, null, title, referenceId, false);
        emit(NotificationEventType.CREATED, created);
        return created;
    }

    /**
     * Notifies the user who submitted an async operation that it was accepted, so they can track
     * its progress from their notifications, and records one {@link NotificationItemOutcome#PENDING}
     * {@link NotificationItem} per targeted resource. {@code userId} is {@code null} for OSS's
     * single implicit user (no real user model).
     */
    public void notifyAsyncOperation(@Nullable String userId, String tenantId, String operationId, AsyncOperationType operationType, List<String> resourceIds) {
        Notification created = createNotification(
            userId,
            tenantId,
            CoreNotificationType.ASYNC_OPERATION,
            operationType,
            "%s requested for %d item%s".formatted(humanize(operationType), resourceIds.size(), resourceIds.size() == 1 ? "" : "s"),
            operationId,
            !resourceIds.isEmpty()
        );

        if (!resourceIds.isEmpty()) {
            Instant now = Instant.now();
            notificationItemRepository.create(
                resourceIds.stream()
                    .map(
                        resourceId -> NotificationItem.builder()
                            .operationId(operationId)
                            .tenantId(tenantId)
                            .resourceId(resourceId)
                            .updated(now)
                            .build()
                    )
                    .toList()
            );
        }

        emit(NotificationEventType.CREATED, created);
    }

    private static String humanize(AsyncOperationType operationType) {
        String words = operationType.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private Notification createNotification(
        @Nullable String userId,
        @Nullable String tenantId,
        NotificationType type,
        @Nullable AsyncOperationType asyncOperationType,
        String title,
        @Nullable String referenceId,
        boolean read) {
        Instant now = Instant.now();

        return notificationRepository.create(
            Notification.builder()
                .id(IdUtils.create())
                .userId(userId)
                .tenantId(tenantId)
                .type(type.name())
                .asyncOperationType(asyncOperationType != null ? asyncOperationType.name() : null)
                .resourceType(asyncOperationType != null ? asyncOperationType.resourceType() : null)
                .title(title)
                .referenceId(referenceId)
                .read(read)
                .createdDate(now)
                .updatedDate(now)
                .build()
        );
    }

    /**
     * Upserts the {@link NotificationItem} tracking {@code (operationId, resourceId)} to {@code outcome}
     * and pushes an update to SSE followers.
     */
    public void updateNotificationItemOutcome(String operationId, @Nullable String tenantId, String resourceId, NotificationItemOutcome outcome) {
        notificationItemRepository.update(
            NotificationItem.builder()
                .operationId(operationId)
                .tenantId(tenantId)
                .resourceId(resourceId)
                .outcome(outcome)
                .updated(Instant.now())
                .build()
        );

        emit(NotificationEventType.UPDATED, null, operationId, tenantId);
    }

    /**
     * Projects {@code succeededItems}/{@code failedItems}/{@code totalItems} onto the notification
     * by aggregating its {@link NotificationItem}s
     */
    private Notification withProgress(Notification notification, boolean upToDate) {
        if (notification.getAsyncOperationType() == null) {
            return notification;
        }

        Map<NotificationItemOutcome, Long> counts = upToDate
            ? notificationItemRepository.countUpToDateOperationOutcomesByOperationId(notification.getTenantId(), notification.getReferenceId())
            : notificationItemRepository.countOperationOutcomesByOperationId(notification.getTenantId(), notification.getReferenceId());
        long succeeded = counts.getOrDefault(NotificationItemOutcome.SUCCEEDED, 0L);
        long failed = counts.getOrDefault(NotificationItemOutcome.FAILED, 0L);
        long pending = counts.getOrDefault(NotificationItemOutcome.PENDING, 0L);

        return notification.toBuilder()
            .succeededItems((int) succeeded)
            .failedItems((int) failed)
            .totalItems((int) (succeeded + failed + pending))
            .build();
    }

    private List<Notification> withProgress(List<Notification> notifications) {
        return notifications.stream().map(notification -> withProgress(notification, false)).toList();
    }

    private void emit(NotificationEventType eventType, Notification notification) {
        emit(eventType, notification.getId(), notification.getReferenceId(), notification.getTenantId());
    }

    private void emit(NotificationEventType eventType, @Nullable String notificationId, @Nullable String referenceId, @Nullable String tenantId) {
        try {
            notificationQueue.emit(NotificationEvent.of(eventType, notificationId, referenceId, tenantId));
        } catch (QueueException e) {
            log.error("Failed to emit NotificationEvent for notification '{}'", notificationId, e);
        }
    }

    public boolean markRead(@Nullable String userId, String id) {
        boolean updated = notificationRepository.markRead(userId, id);
        if (updated) {
            notificationRepository.findById(userId, id)
                .ifPresent(notification -> emit(NotificationEventType.UPDATED, id, notification.getReferenceId(), notification.getTenantId()));
        }
        return updated;
    }

    public boolean markUnread(@Nullable String userId, String id) {
        boolean updated = notificationRepository.markUnread(userId, id);
        if (updated) {
            notificationRepository.findById(userId, id)
                .ifPresent(notification -> emit(NotificationEventType.UPDATED, id, notification.getReferenceId(), notification.getTenantId()));
        }
        return updated;
    }

    public int markAllRead(@Nullable String userId) {
        List<Notification> updated = notificationRepository.markAllRead(userId, accessibleTenantIds(userId));
        emit(NotificationEventType.UPDATED, updated);
        return updated.size();
    }

    public long countUnread(@Nullable String userId) {
        return notificationRepository.countUnread(userId, accessibleTenantIds(userId));
    }

    /**
     * History, cursor-based, hydrated with progress (see {@link #withProgress(Notification, boolean)}).
     */
    public List<Notification> findByUser(@Nullable String userId, @Nullable NotificationCursor cursor, int limit) {
        return withProgress(notificationRepository.findByUser(userId, accessibleTenantIds(userId), cursor, limit));
    }

    /**
     * Polling delta, hydrated with progress (see {@link #withProgress(Notification, boolean)}).
     */
    public List<Notification> findByUserSince(@Nullable String userId, Instant since) {
        return withProgress(notificationRepository.findByUserSince(userId, accessibleTenantIds(userId), since));
    }

    /**
     * The tenants {@code userId} currently has access to. Overridden in EE with the user's real
     * RBAC-derived set.
     */
    protected Set<String> accessibleTenantIds(@Nullable String userId) {
        return Set.of(TenantService.MAIN_TENANT);
    }

    /**
     * Follows live notification updates for {@code userId}.
     * Updates are sampled per notification id, so a fast-changing notification
     * (e.g. progress ticks) can't flood the stream.
     * <p>
     * Callers must invoke {@link FollowSubscription#unregister()} once the stream terminates.
     */
    public FollowSubscription follow(@Nullable String userId) {
        String subscriberId = IdUtils.create();

        Flux<Event<Notification>> flux = Flux.<NotificationEvent> create(
            emitter -> notificationStreamingService.registerSubscriber(userId, subscriberId, emitter),
            FluxSink.OverflowStrategy.LATEST
        )
            .doFinally(_ -> notificationStreamingService.unregisterSubscriber(userId, subscriberId))
            .buffer(NOTIFICATION_SAMPLE_INTERVAL)
            .flatMapIterable(events -> latestEventByNotification(userId, events))
            .flatMap(this::toEvent)
            .map(this::withProgress)
            .timeout(Duration.ofHours(1));

        return new FollowSubscription(flux, () -> notificationStreamingService.unregisterSubscriber(userId, subscriberId));
    }

    private Mono<Event<Notification>> toEvent(NotificationEvent event) {
        return Mono.justOrEmpty(notificationRepository.findByNotificationIdOrReferenceId(event.notificationId(), event.referenceId()))
            .map(notification -> Event.of(notification).id(event.eventType().name().toLowerCase()));
    }

    private Event<Notification> withProgress(Event<Notification> event) {
        return Event.of(event, withProgress(event.getData(), true));
    }

    private List<NotificationEvent> latestEventByNotification(@Nullable String userId, List<NotificationEvent> events) {
        if (events.isEmpty()) {
            return events;
        }

        Set<String> accessibleTenantIds = accessibleTenantIds(userId);

        return events.stream()
            .filter(event -> isAccessible(accessibleTenantIds, event.tenantId()))
            .collect(
                Collectors.toMap(
                    event -> event.notificationId() != null ? event.notificationId() : event.referenceId(),
                    Function.identity(),
                    (first, last) -> last,
                    LinkedHashMap::new
                )
            )
            .values()
            .stream()
            .toList();
    }

    private static boolean isAccessible(Set<String> accessibleTenantIds, @Nullable String tenantId) {
        return tenantId == null || accessibleTenantIds.contains(tenantId);
    }

    /**
     * Flat-TTL retention purge: read notifications older than {@value READ_RETENTION_DAYS} days,
     * and any notification older than {@value UNREAD_RETENTION_DAYS} days regardless of read state.
     * Cascades to the purged notifications' {@link NotificationItem}s, via each async-operation
     * notification's {@code (tenantId, referenceId)}.
     *
     * @return the number of deleted notification rows.
     */
    public int purge() {
        Instant now = Instant.now();
        Instant readOlderThan = now.minus(READ_RETENTION_DAYS, ChronoUnit.DAYS);
        Instant createdOlderThan = now.minus(UNREAD_RETENTION_DAYS, ChronoUnit.DAYS);

        List<NotificationItemRepositoryInterface.TenantOperationId> operationIds = notificationRepository.findToPurge(readOlderThan, createdOlderThan).stream()
            .filter(notification -> notification.getAsyncOperationType() != null)
            .map(notification -> new NotificationItemRepositoryInterface.TenantOperationId(notification.getTenantId(), notification.getReferenceId()))
            .toList();
        if (!operationIds.isEmpty()) {
            notificationItemRepository.deleteByOperationIds(operationIds);
        }

        return notificationRepository.deleteByQuery(readOlderThan, createdOlderThan);
    }

    @SneakyThrows(QueueException.class)
    private void emit(NotificationEventType eventType, List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        notificationQueue
            .emit(notifications.stream().map(notification -> NotificationEvent.of(eventType, notification.getId(), notification.getReferenceId(), notification.getTenantId())).toList());
    }

    /**
     * A live notification stream and the cleanup its caller must run once the stream terminates
     * (complete / error / cancel), to unregister the subscriber from {@link NotificationStreamingService}.
     */
    public record FollowSubscription(Flux<Event<Notification>> flux, Runnable unregister) {
    }
}
