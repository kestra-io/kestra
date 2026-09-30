package io.kestra.core.services;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.models.notifications.CoreNotificationType;
import io.kestra.core.models.notifications.Notification;
import io.kestra.core.models.notifications.NotificationEvent;
import io.kestra.core.models.notifications.NotificationEventType;
import io.kestra.core.models.notifications.NotificationType;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.NotificationRepositoryInterface;
import io.kestra.core.server.AsyncOperationType;
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
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

/**
 * Write-path facade for {@link Notification}s. This is the seam producers (an EE case-management
 * feature, async-operation progress, ...) target: it writes synchronously to the repository today,
 * but callers never see the persistence mechanism, so the write path can move behind a durable
 * queue later with zero caller changes.
 * <p>
 * Every create/update also emits a {@link NotificationEvent} on {@link #notificationQueue}, so
 * consumers (e.g. a live UI bell) don't have to poll — {@link #follow(String)} is the read-path
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
    private static final Duration ACCESSIBLE_TENANT_IDS_REFRESH_INTERVAL = Duration.ofSeconds(1);
    private static final Duration NOTIFICATION_SAMPLE_INTERVAL = Duration.ofMillis(250);

    private final NotificationRepositoryInterface notificationRepository;
    private final BroadcastQueueInterface<NotificationEvent> notificationQueue;
    private final NotificationStreamingService notificationStreamingService;
    private final AccessibleTenantsProvider accessibleTenantsProvider;
    private final CurrentUserProvider currentUserProvider;
    private final TenantService tenantService;

    @Inject
    public NotificationService(
        NotificationRepositoryInterface notificationRepository,
        BroadcastQueueInterface<NotificationEvent> notificationQueue,
        NotificationStreamingService notificationStreamingService,
        AccessibleTenantsProvider accessibleTenantsProvider,
        CurrentUserProvider currentUserProvider,
        TenantService tenantService) {
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository must not be null");
        this.notificationQueue = Objects.requireNonNull(notificationQueue, "notificationQueue must not be null");
        this.notificationStreamingService = Objects.requireNonNull(notificationStreamingService, "notificationStreamingService must not be null");
        this.accessibleTenantsProvider = Objects.requireNonNull(accessibleTenantsProvider, "accessibleTenantsProvider must not be null");
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider, "currentUserProvider must not be null");
        this.tenantService = Objects.requireNonNull(tenantService, "tenantService must not be null");
    }

    /**
     * Creates a new, unread notification.
     */
    public Notification notify(String userId, @Nullable String tenantId, NotificationType type, String title, @Nullable String referenceId) {
        return notify(userId, tenantId, type, title, referenceId, null);
    }

    /**
     * Creates a new, unread notification tracking progress out of {@code totalItems}, with
     * {@code succeededItems} and {@code failedItems} initialized to 0.
     */
    public Notification notify(String userId, @Nullable String tenantId, NotificationType type, String title, @Nullable String referenceId, @Nullable Integer totalItems) {
        return notify(userId, tenantId, type, null, title, referenceId, totalItems);
    }

    /**
     * Notifies the user who submitted an async operation that it was accepted, so they can track
     * its progress from their notifications.
     */
    public void notifyAsyncOperation(String operationId, AsyncOperationType operationType, int itemCount) {
        currentUserProvider.currentUserId().ifPresent(
            userId -> notify(
                userId,
                tenantService.resolveTenant(),
                CoreNotificationType.ASYNC_OPERATION,
                operationType,
                "%s requested for %d item%s".formatted(humanize(operationType), itemCount, itemCount == 1 ? "" : "s"),
                operationId,
                itemCount
            )
        );
    }

    private static String humanize(AsyncOperationType operationType) {
        String words = operationType.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /**
     * Same as {@link #notify(String, String, NotificationType, String, String, Integer)}, additionally tagging
     * the notification with the specific {@link AsyncOperationType} it reports on.
     */
    public Notification notify(
        String userId,
        @Nullable String tenantId,
        NotificationType type,
        @Nullable AsyncOperationType asyncOperationType,
        String title,
        @Nullable String referenceId,
        @Nullable Integer totalItems) {
        Instant now = Instant.now();

        // no read/not read for operation progresses
        boolean isRead = totalItems != null && totalItems > 0;

        Notification created = notificationRepository.create(
            Notification.builder()
                .id(IdUtils.create())
                .userId(userId)
                .tenantId(tenantId)
                .type(type.key())
                .asyncOperationType(asyncOperationType)
                .title(title)
                .referenceId(referenceId)
                .succeededItems(totalItems != null ? 0 : null)
                .failedItems(totalItems != null ? 0 : null)
                .totalItems(totalItems)
                .read(isRead)
                .createdDate(now)
                .updatedDate(now)
                .build()
        );
        emit(NotificationEventType.CREATED, created);
        return created;
    }

    /**
     * Upserts progress on an existing notification, found by its correlation key
     * {@code (userId, type, referenceId)}.
     *
     * @throws NotFoundException if no notification matches the correlation key.
     */
    public Notification updateProgress(String userId, NotificationType type, String referenceId, int succeeded, int failed, int total) {
        Notification existing = notificationRepository.findByUserTypeAndReferenceId(userId, type.key(), referenceId)
            .orElseThrow(() -> new NotFoundException("No notification found for user '" + userId + "', type '" + type.key() + "', referenceId '" + referenceId + "'"));

        Notification updated = notificationRepository.update(
            existing.toBuilder()
                .succeededItems(succeeded)
                .failedItems(failed)
                .totalItems(total)
                .updatedDate(Instant.now())
                .build()
        );
        emit(NotificationEventType.UPDATED, updated);
        return updated;
    }

    /**
     * Increments the succeeded-items counter of the notification tracking {@code operationId}.
     */
    public void incrementAsyncOperationSucceededItems(String operationId, int count) {
        incrementProgress(operationId, count, 0);
    }

    /**
     * Increments the failed-items counter of the notification tracking {@code operationId}.
     */
    public void incrementAsyncOperationFailedItems(String operationId, int count) {
        incrementProgress(operationId, 0, count);
    }

    private void incrementProgress(String operationId, int succeededDelta, int failedDelta) {
        Notification existing = notificationRepository.findByOperationId(operationId)
            .orElseThrow(() -> new NotFoundException("No notification found for operationId '" + operationId + "'"));

        int currentSucceededItems = existing.getSucceededItems() == null ? 0 : existing.getSucceededItems();
        int currentFailedItems = existing.getFailedItems() == null ? 0 : existing.getFailedItems();

        Notification updated = notificationRepository.update(
            existing.toBuilder()
                .succeededItems(currentSucceededItems + succeededDelta)
                .failedItems(currentFailedItems + failedDelta)
                .updatedDate(Instant.now())
                .build()
        );
        emit(NotificationEventType.UPDATED, updated);
    }

    private void emit(NotificationEventType eventType, Notification notification) {
        try {
            notificationQueue.emit(NotificationEvent.of(eventType, notification));
        } catch (QueueException e) {
            log.error("Failed to emit NotificationEvent for notification '{}'", notification.getId(), e);
        }
    }

    public boolean markRead(String userId, String id) {
        boolean updated = notificationRepository.markRead(userId, id);
        if (updated) {
            notificationRepository.findById(userId, id).ifPresent(notification -> emit(NotificationEventType.UPDATED, notification));
        }
        return updated;
    }

    public boolean markUnread(String userId, String id) {
        boolean updated = notificationRepository.markUnread(userId, id);
        if (updated) {
            notificationRepository.findById(userId, id).ifPresent(notification -> emit(NotificationEventType.UPDATED, notification));
        }
        return updated;
    }

    public int markAllRead(String userId, Set<String> accessibleTenantIds) {
        List<Notification> updated = notificationRepository.markAllRead(userId, accessibleTenantIds);
        emit(NotificationEventType.UPDATED, updated);
        return updated.size();
    }

    /**
     * Follows live notification updates for {@code userId}, restricted to their currently
     * accessible tenants. Each event is checked against the latest value of {@link #accessibleTenantIds}.
     * Updates are sampled per notification id, so a fast-changing notification (e.g. progress ticks)
     * can't flood the stream at the expense of others.
     * <p>
     * Callers must invoke {@link FollowSubscription#unregister()} once the stream terminates.
     */
    public FollowSubscription follow(String userId) {
        String subscriberId = IdUtils.create();
        Set<String> initialAccessibleTenantIds = accessibleTenantIds(userId);

        Flux<Event<Notification>> flux = Flux.<NotificationEvent> create(
            emitter -> notificationStreamingService.registerSubscriber(userId, subscriberId, emitter),
            FluxSink.OverflowStrategy.BUFFER
        )
            .doFinally(_ -> notificationStreamingService.unregisterSubscriber(userId, subscriberId))
            .withLatestFrom(upToDateAccessibleTenantsIds(userId, initialAccessibleTenantIds), Tuples::of)
            .filter(notificationAndAllowedTenants -> isAccessible(notificationAndAllowedTenants.getT2(), notificationAndAllowedTenants.getT1().notification()))
            .map(tuple -> Event.of(tuple.getT1().notification()).id(tuple.getT1().eventType().name().toLowerCase()))
            .buffer(NOTIFICATION_SAMPLE_INTERVAL)
            .flatMapIterable(NotificationService::latestNotificationUpdateById)
            .timeout(Duration.ofHours(1));

        return new FollowSubscription(flux, () -> notificationStreamingService.unregisterSubscriber(userId, subscriberId));
    }

    private static List<Event<Notification>> latestNotificationUpdateById(List<Event<Notification>> events) {
        return events.stream()
            .collect(
                Collectors.toMap(
                    event -> event.getData().getId(),
                    Function.identity(),
                    (first, last) -> last,
                    LinkedHashMap::new
                )
            )
            .values()
            .stream()
            .toList();
    }

    private Flux<Set<String>> upToDateAccessibleTenantsIds(String userId, Set<String> initialAccessibleTenantIds) {
        return Flux.interval(ACCESSIBLE_TENANT_IDS_REFRESH_INTERVAL, Schedulers.boundedElastic())
            .map(_ -> accessibleTenantIds(userId))
            .startWith(initialAccessibleTenantIds);
    }

    private static boolean isAccessible(Set<String> accessibleTenantIds, Notification notification) {
        String tenantId = notification.getTenantId();
        return tenantId == null || accessibleTenantIds.contains(tenantId);
    }

    /**
     * Gets the accessible tenants for the given user.
     */
    public Set<String> accessibleTenantIds(String userId) {
        return accessibleTenantsProvider.accessibleTenantIds(userId);
    }

    /**
     * Flat-TTL retention purge: read notifications older than {@value READ_RETENTION_DAYS} days,
     * and any notification older than {@value UNREAD_RETENTION_DAYS} days regardless of read state.
     *
     * @return the number of deleted rows.
     */
    public int purge() {
        Instant now = Instant.now();
        return notificationRepository.deleteByQuery(
            now.minus(READ_RETENTION_DAYS, ChronoUnit.DAYS),
            now.minus(UNREAD_RETENTION_DAYS, ChronoUnit.DAYS)
        );
    }

    @SneakyThrows(QueueException.class)
    private void emit(NotificationEventType eventType, List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        notificationQueue.emit(notifications.stream().map(notification -> NotificationEvent.of(eventType, notification)).toList());
    }

    /**
     * A live notification stream and the cleanup its caller must run once the stream terminates
     * (complete / error / cancel), to unregister the subscriber from {@link NotificationStreamingService}.
     */
    public record FollowSubscription(Flux<Event<Notification>> flux, Runnable unregister) {
    }
}
