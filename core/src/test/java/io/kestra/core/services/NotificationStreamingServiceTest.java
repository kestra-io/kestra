package io.kestra.core.services;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.notifications.CoreNotificationType;
import io.kestra.core.models.notifications.Notification;
import io.kestra.core.models.notifications.NotificationEvent;
import io.kestra.core.models.notifications.NotificationEventType;
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
    private NotificationStreamingService service;

    @Test
    void shouldDeliverEventToSubscriberOfSameUser() throws Exception {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        String subscriberId = "sub-1";
        Notification notification = notification(userId, null);

        CompletableFuture<NotificationEvent> future = subscribe(userId, subscriberId);

        notificationQueue.emit(NotificationEvent.of(NotificationEventType.CREATED, notification));

        NotificationEvent received = future.get(5, TimeUnit.SECONDS);
        assertThat(received.notification().getId()).isEqualTo(notification.getId());
    }

    @Test
    void shouldNotDeliverEventToSubscriberOfDifferentUser() throws Exception {
        String subscribedUserId = TestsUtils.randomString(this.getClass().getSimpleName() + "-subscribed");
        String otherUserId = TestsUtils.randomString(this.getClass().getSimpleName() + "-other");
        String subscriberId = "sub-1";

        CompletableFuture<NotificationEvent> future = subscribe(subscribedUserId, subscriberId);

        // Emitted before the sentinel: if it were (wrongly) delivered, it would complete the future first.
        notificationQueue.emit(NotificationEvent.of(NotificationEventType.CREATED, notification(otherUserId, null)));
        Notification sentinel = notification(subscribedUserId, null);
        notificationQueue.emit(NotificationEvent.of(NotificationEventType.CREATED, sentinel));

        NotificationEvent received = future.get(5, TimeUnit.SECONDS);
        assertThat(received.notification().getId()).isEqualTo(sentinel.getId());
    }

    @Test
    void shouldNotBlowUpWhenNoSubscriberForEmittedEvent() throws Exception {
        notificationQueue.emit(NotificationEvent.of(NotificationEventType.CREATED, notification(TestsUtils.randomString(this.getClass().getSimpleName()), null)));
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
            .type(CoreNotificationType.GENERIC.key())
            .title("title")
            .read(false)
            .createdDate(now)
            .updatedDate(now)
            .build();
    }
}
