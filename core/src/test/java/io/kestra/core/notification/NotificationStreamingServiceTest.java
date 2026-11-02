package io.kestra.core.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.notification.model.CoreNotificationType;
import io.kestra.core.notification.model.Notification;
import io.kestra.core.notification.model.NotificationEvent;
import io.kestra.core.notification.model.NotificationEventType;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class NotificationStreamingServiceTest {

    @Inject
    private BroadcastQueueInterface<NotificationEvent> notificationQueue;

    @Inject
    private NotificationRepositoryInterface notificationRepository;

    @Inject
    private NotificationStreamingService service;

    @Test
    void shouldDeliverEventToSubscriberOfSameUser() throws Exception {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        String subscriberId = "sub-1";
        Notification notification = notificationRepository.create(notification(userId, null));

        CompletableFuture<NotificationEvent> future = subscribe(userId, subscriberId);

        notificationQueue.emit(NotificationEvent.of(NotificationEventType.CREATED, notification.getId(), notification.getReferenceId(), notification.getTenantId()));

        NotificationEvent received = future.get(5, TimeUnit.SECONDS);
        assertThat(received.notificationId()).isEqualTo(notification.getId());
    }

    @Test
    void shouldNotDeliverEventToSubscriberOfDifferentUser() throws Exception {
        String subscribedUserId = TestsUtils.randomString(this.getClass().getSimpleName() + "-subscribed");
        String otherUserId = TestsUtils.randomString(this.getClass().getSimpleName() + "-other");
        String subscriberId = "sub-1";

        CompletableFuture<NotificationEvent> future = subscribe(subscribedUserId, subscriberId);

        Notification other = notificationRepository.create(notification(otherUserId, null));
        Notification sentinel = notificationRepository.create(notification(subscribedUserId, null));

        // Emitted before the sentinel: if it were (wrongly) delivered, it would complete the future first.
        notificationQueue.emit(NotificationEvent.of(NotificationEventType.CREATED, other.getId(), other.getReferenceId(), other.getTenantId()));
        notificationQueue.emit(NotificationEvent.of(NotificationEventType.CREATED, sentinel.getId(), sentinel.getReferenceId(), sentinel.getTenantId()));

        NotificationEvent received = future.get(5, TimeUnit.SECONDS);
        assertThat(received.notificationId()).isEqualTo(sentinel.getId());
    }

    @Test
    void shouldNotBlowUpWhenNoSubscriberForEmittedEvent() throws Exception {
        Notification notification = notificationRepository.create(notification(TestsUtils.randomString(this.getClass().getSimpleName()), null));
        notificationQueue.emit(NotificationEvent.of(NotificationEventType.CREATED, notification.getId(), notification.getReferenceId(), notification.getTenantId()));
    }

    @Test
    void shouldNotBlowUpWhenEventReferencesAnUnknownNotification() throws Exception {
        notificationQueue.emit(NotificationEvent.of(NotificationEventType.CREATED, "unknown-id", null, null));
    }

    private CompletableFuture<NotificationEvent> subscribe(String userId, String subscriberId) {
        CompletableFuture<NotificationEvent> future = new CompletableFuture<>();
        Flux.<NotificationEvent> create(sink -> service.registerSubscriber(userId, subscriberId, sink))
            .timeout(Duration.ofSeconds(5))
            .doFinally(signal -> service.unregisterSubscriber(userId, subscriberId))
            .subscribe(future::complete, future::completeExceptionally);
        return future;
    }

    private static Notification notification(String userId, String tenantId) {
        Instant now = Instant.now();
        return Notification.builder()
            .id(IdUtils.create())
            .userId(userId)
            .tenantId(tenantId)
            .type(CoreNotificationType.GENERIC.name())
            .title("title")
            .read(false)
            .createdDate(now)
            .updatedDate(now)
            .build();
    }
}
