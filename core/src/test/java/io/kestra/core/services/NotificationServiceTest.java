package io.kestra.core.services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.notifications.CoreNotificationType;
import io.kestra.core.models.notifications.Notification;
import io.kestra.core.models.notifications.NotificationEvent;
import io.kestra.core.models.notifications.NotificationItemOutcome;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.repositories.NotificationItemRepositoryInterface;
import io.kestra.core.repositories.NotificationRepositoryInterface;
import io.kestra.core.server.AsyncOperationType;
import io.kestra.core.server.CoreAsyncOperationType;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@MicronautTest
public abstract class NotificationServiceTest {

    private static final String USER_ID = "test-user";

    @Inject
    private NotificationRepositoryInterface notificationRepository;

    @Inject
    private NotificationItemRepositoryInterface notificationItemRepository;

    @Inject
    private BroadcastQueueInterface<NotificationEvent> notificationQueue;

    @Inject
    private NotificationStreamingService notificationStreamingService;

    private NotificationService notificationService;

    @BeforeEach
    public void initNotificationService() {
        notificationService = new NotificationService(
            notificationRepository,
            notificationItemRepository,
            notificationQueue,
            notificationStreamingService
        );
    }

    @Test
    void shouldCreateUnreadNotificationGivenNotify() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());

        Notification notification = notificationService.notify(userId, "tenantA", CoreNotificationType.GENERIC, "title", "ref-1");

        assertThat(notification.getId()).isNotNull();
        assertThat(notification.getUserId()).isEqualTo(userId);
        assertThat(notification.getTenantId()).isEqualTo("tenantA");
        assertThat(notification.getTitle()).isEqualTo("title");
        assertThat(notification.getReferenceId()).isEqualTo("ref-1");
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getCreatedDate()).isNotNull();
        assertThat(notification.getUpdatedDate()).isNotNull();
        assertThat(notification.getTotalItems()).isNull();
        assertThat(notification.getSucceededItems()).isNull();
        assertThat(notification.getFailedItems()).isNull();
        assertThat(notification.getResourceType()).isNull();
    }

    @Test
    void shouldCreatePendingItemsGivenNotifyAsyncOperation() {
        notificationService.notifyAsyncOperation(USER_ID, TenantService.MAIN_TENANT, "op-items-1", CoreAsyncOperationType.EXECUTION_KILL, List.of("res-1", "res-2", "res-3"));

        Notification notification = notificationRepository.findByOperationId("op-items-1").orElseThrow();
        Map<NotificationItemOutcome, Long> counts = notificationItemRepository.countByOperationId(notification.getTenantId(), "op-items-1");

        assertThat(counts.get(NotificationItemOutcome.PENDING)).isEqualTo(3L);
    }

    @Test
    void shouldCreateNotificationWithNullUserIdGivenNotifyAsyncOperationWithoutAnAuthenticatedUser() {
        notificationService.notifyAsyncOperation(null, TenantService.MAIN_TENANT, "op-no-user", CoreAsyncOperationType.EXECUTION_KILL, List.of("res-1"));

        Notification notification = notificationRepository.findByOperationId("op-no-user").orElseThrow();
        assertThat(notification.getUserId()).isNull();
    }

    @Test
    void shouldRoundTripNotificationsWithNullUserId() {
        Notification notification = notificationService.notify(null, null, CoreNotificationType.GENERIC, "title", null);

        assertThat(notification.getUserId()).isNull();
        assertThat(notificationService.findByUser(null, null, 50)).extracting(Notification::getId).contains(notification.getId());
        assertThat(notificationService.markRead(null, notification.getId())).isTrue();
    }

    @Test
    void shouldProjectProgressOntoNotificationGivenFindByUser() {
        notificationService.notifyAsyncOperation(USER_ID, TenantService.MAIN_TENANT, "op-items-2", CoreAsyncOperationType.EXECUTION_KILL, List.of("res-1", "res-2"));
        notificationService.updateNotificationItemOutcome("op-items-2", TenantService.MAIN_TENANT, "res-1", NotificationItemOutcome.SUCCEEDED);

        List<Notification> notifications = notificationService.findByUser(USER_ID, null, 50);
        Notification notification = notifications.stream()
            .filter(n -> "op-items-2".equals(n.getReferenceId()))
            .findFirst()
            .orElseThrow();

        assertThat(notification.getTotalItems()).isEqualTo(2);
        assertThat(notification.getSucceededItems()).isEqualTo(1);
        assertThat(notification.getFailedItems()).isEqualTo(0);
    }

    @Test
    void shouldFlipItemOutcomeGivenRecordAsyncOperationItemOutcome() {
        notificationService.notifyAsyncOperation(USER_ID, TenantService.MAIN_TENANT, "op-outcome-1", CoreAsyncOperationType.EXECUTION_KILL, List.of("res-1"));

        notificationService.updateNotificationItemOutcome("op-outcome-1", TenantService.MAIN_TENANT, "res-1", NotificationItemOutcome.SUCCEEDED);

        Notification notification = notificationRepository.findByOperationId("op-outcome-1").orElseThrow();
        Map<NotificationItemOutcome, Long> counts = notificationItemRepository.countByOperationId(notification.getTenantId(), "op-outcome-1");
        assertThat(counts.get(NotificationItemOutcome.SUCCEEDED)).isEqualTo(1L);
        assertThat(counts.getOrDefault(NotificationItemOutcome.PENDING, 0L)).isEqualTo(0L);
    }

    @Test
    void shouldUpsertNotificationItemGivenRecordAsyncOperationItemOutcomeForUnknownResource() {
        notificationService.notifyAsyncOperation(USER_ID, TenantService.MAIN_TENANT, "op-outcome-2", CoreAsyncOperationType.EXECUTION_KILL, List.of("res-1"));

        notificationService.updateNotificationItemOutcome("op-outcome-2", TenantService.MAIN_TENANT, "unknown-resource", NotificationItemOutcome.SUCCEEDED);

        Notification notification = notificationRepository.findByOperationId("op-outcome-2").orElseThrow();
        Map<NotificationItemOutcome, Long> counts = notificationItemRepository.countByOperationId(notification.getTenantId(), "op-outcome-2");
        assertThat(counts.get(NotificationItemOutcome.PENDING)).isEqualTo(1L);
        assertThat(counts.get(NotificationItemOutcome.SUCCEEDED)).isEqualTo(1L);
    }

    @Test
    void shouldNotThrowGivenRecordAsyncOperationItemOutcomeWithoutMatchingNotification() {
        assertThatCode(() -> notificationService.updateNotificationItemOutcome("unknown-op", TenantService.MAIN_TENANT, "res-1", NotificationItemOutcome.SUCCEEDED))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldNotifyCurrentUserWithHumanizedTitleGivenOnAsyncOperationCreated() {
        // EXECUTION_FORCE_RUN exercises a multi-word enum name, unlike EXECUTION_KILL below.
        notificationService.notifyAsyncOperation(USER_ID, TenantService.MAIN_TENANT, "op-created-1", CoreAsyncOperationType.EXECUTION_FORCE_RUN, List.of("res-1", "res-2", "res-3"));

        Notification notification = notificationRepository.findByOperationId("op-created-1").orElseThrow();
        assertThat(notification.getUserId()).isEqualTo(USER_ID);
        assertThat(notification.getTenantId()).isEqualTo(TenantService.MAIN_TENANT);
        assertThat(notification.getType()).isEqualTo(CoreNotificationType.ASYNC_OPERATION.name());
        assertThat(notification.getTitle()).isEqualTo("Execution force run requested for 3 items");
        assertThat(notification.getResourceType()).isEqualTo(AsyncOperationType.ResourceType.EXECUTION);
    }

    @Test
    void shouldUseSingularWordingForASingleItemGivenOnAsyncOperationCreated() {
        notificationService.notifyAsyncOperation(USER_ID, TenantService.MAIN_TENANT, "op-created-2", CoreAsyncOperationType.EXECUTION_KILL, List.of("res-1"));

        Notification notification = notificationRepository.findByOperationId("op-created-2").orElseThrow();
        assertThat(notification.getTitle()).isEqualTo("Execution kill requested for 1 item");
    }

    @Test
    void shouldMarkNotificationAsReadGivenMarkRead() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        Notification notification = notificationService.notify(userId, null, CoreNotificationType.GENERIC, "title", null);

        assertThat(notificationService.markRead(userId, notification.getId())).isTrue();
        assertThat(notificationRepository.findById(userId, notification.getId()).orElseThrow().isRead()).isTrue();

        assertThat(notificationService.markRead(userId, "unknown-id")).isFalse();
    }

    @Test
    void shouldMarkNotificationAsUnreadGivenMarkUnread() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        Notification notification = notificationService.notify(userId, null, CoreNotificationType.GENERIC, "title", null);
        notificationService.markRead(userId, notification.getId());

        assertThat(notificationService.markUnread(userId, notification.getId())).isTrue();
        assertThat(notificationRepository.findById(userId, notification.getId()).orElseThrow().isRead()).isFalse();

        assertThat(notificationService.markUnread(userId, "unknown-id")).isFalse();
    }

    @Test
    void shouldMarkAllAsReadOnlyForAccessibleTenantsGivenMarkAllRead() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationService.notify(userId, null, CoreNotificationType.GENERIC, "accessible", null);
        notificationService.notify(userId, "some-other-tenant", CoreNotificationType.GENERIC, "inaccessible", null);

        int updated = notificationService.markAllRead(userId);

        assertThat(updated).isEqualTo(1);
    }

    @Test
    void shouldDeleteReadOlderThanSevenDaysAndAnyOlderThanThirtyDaysGivenPurge() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        Instant now = Instant.now();

        Notification readOld = notify(userId, "readOld", true, now.minus(10, ChronoUnit.DAYS));
        Notification readRecent = notify(userId, "readRecent", true, now.minus(1, ChronoUnit.DAYS));
        Notification unreadOld = notify(userId, "unreadOld", false, now.minus(31, ChronoUnit.DAYS));
        Notification unreadRecent = notify(userId, "unreadRecent", false, now.minus(1, ChronoUnit.DAYS));

        int deleted = notificationService.purge();

        assertThat(deleted).isGreaterThanOrEqualTo(2);
        assertThat(notificationRepository.findById(userId, readOld.getId())).isEmpty();
        assertThat(notificationRepository.findById(userId, unreadOld.getId())).isEmpty();
        assertThat(notificationRepository.findById(userId, readRecent.getId())).isPresent();
        assertThat(notificationRepository.findById(userId, unreadRecent.getId())).isPresent();
    }

    @Test
    void shouldCascadeDeleteItemsGivenPurge() {
        notificationService.notifyAsyncOperation(USER_ID, TenantService.MAIN_TENANT, "op-purge-1", CoreAsyncOperationType.EXECUTION_KILL, List.of("res-1"));
        Notification notification = notificationRepository.findByOperationId("op-purge-1").orElseThrow();
        Notification aged = notification.toBuilder()
            .createdDate(Instant.now().minus(31, ChronoUnit.DAYS))
            .updatedDate(Instant.now().minus(31, ChronoUnit.DAYS))
            .build();
        notificationRepository.update(aged);

        notificationService.purge();

        assertThat(notificationItemRepository.countByOperationId(notification.getTenantId(), "op-purge-1")).isEmpty();
    }

    private Notification notify(String userId, String suffix, boolean read, Instant createdDate) {
        Notification created = notificationService.notify(userId, null, CoreNotificationType.GENERIC, "title-" + suffix, null);
        Notification aged = created.toBuilder().read(read).createdDate(createdDate).updatedDate(createdDate).build();
        return notificationRepository.update(aged);
    }
}
