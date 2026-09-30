package io.kestra.core.services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.models.notifications.CoreNotificationType;
import io.kestra.core.models.notifications.Notification;
import io.kestra.core.repositories.NotificationRepositoryInterface;
import io.kestra.core.server.AsyncOperationType;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MicronautTest
public abstract class NotificationServiceTest {

    @Inject
    private NotificationService notificationService;

    @Inject
    private NotificationRepositoryInterface notificationRepository;

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
    }

    @Test
    void shouldInitializeProgressCountersGivenNotifyWithTotalItems() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());

        Notification notification = notificationService.notify(userId, "tenantA", CoreNotificationType.ASYNC_OPERATION, "title", "op-1", 5);

        assertThat(notification.getTotalItems()).isEqualTo(5);
        assertThat(notification.getSucceededItems()).isEqualTo(0);
        assertThat(notification.getFailedItems()).isEqualTo(0);
    }

    @Test
    void shouldUpdateExistingNotificationGivenUpdateProgressWithMatchingCorrelationKey() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationService.notify(userId, null, CoreNotificationType.GENERIC, "title", "op-1");

        Notification updated = notificationService.updateProgress(userId, CoreNotificationType.GENERIC, "op-1", 2, 5, 7);

        assertThat(updated.getSucceededItems()).isEqualTo(2);
        assertThat(updated.getFailedItems()).isEqualTo(5);
        assertThat(updated.getTotalItems()).isEqualTo(7);

        Optional<Notification> reloaded = notificationRepository.findByUserTypeAndReferenceId(userId, CoreNotificationType.GENERIC.key(), "op-1");
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getSucceededItems()).isEqualTo(2);
    }

    @Test
    void shouldThrowNotFoundGivenUpdateProgressWithoutMatchingNotification() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());

        assertThatThrownBy(() -> notificationService.updateProgress(userId, CoreNotificationType.GENERIC, "unknown-ref", 1, 2, 3))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldAccumulateCountersGivenIncrementSucceededAndFailedItems() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationService.notify(userId, "tenantA", CoreNotificationType.ASYNC_OPERATION, "title", "op-increment", 3);

        notificationService.incrementAsyncOperationSucceededItems("op-increment", 1);
        notificationService.incrementAsyncOperationSucceededItems("op-increment", 1);
        notificationService.incrementAsyncOperationFailedItems("op-increment", 1);

        Notification reloaded = notificationRepository.findByOperationId("op-increment").orElseThrow();
        assertThat(reloaded.getSucceededItems()).isEqualTo(2);
        assertThat(reloaded.getFailedItems()).isEqualTo(1);
        assertThat(reloaded.getTotalItems()).isEqualTo(3);
    }

    @Test
    void shouldThrowNotFoundGivenIncrementItemsWithoutMatchingNotification() {
        assertThatThrownBy(() -> notificationService.incrementAsyncOperationSucceededItems("unknown-op", 1))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> notificationService.incrementAsyncOperationFailedItems("unknown-op", 1))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldNotifyCurrentUserWithHumanizedTitleGivenOnAsyncOperationCreated() {
        // EXECUTION_FORCE_RUN exercises a multi-word enum name, unlike EXECUTION_KILL below.
        notificationService.notifyAsyncOperation("op-created-1", AsyncOperationType.EXECUTION_FORCE_RUN, 3);

        Notification notification = notificationRepository.findByOperationId("op-created-1").orElseThrow();
        assertThat(notification.getUserId()).isEqualTo(CurrentUserProvider.DEFAULT_USER_ID);
        assertThat(notification.getTenantId()).isEqualTo(TenantService.MAIN_TENANT);
        assertThat(notification.getType()).isEqualTo(CoreNotificationType.ASYNC_OPERATION.key());
        assertThat(notification.getTitle()).isEqualTo("Execution force run requested for 3 items");
        assertThat(notification.getTotalItems()).isEqualTo(3);
    }

    @Test
    void shouldUseSingularWordingForASingleItemGivenOnAsyncOperationCreated() {
        notificationService.notifyAsyncOperation("op-created-2", AsyncOperationType.EXECUTION_KILL, 1);

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
        notificationService.notify(userId, "tenantA", CoreNotificationType.GENERIC, "accessible", null);
        notificationService.notify(userId, "tenantB", CoreNotificationType.GENERIC, "inaccessible", null);

        int updated = notificationService.markAllRead(userId, Set.of("tenantA"));

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

    private Notification notify(String userId, String suffix, boolean read, Instant createdDate) {
        Notification created = notificationService.notify(userId, null, CoreNotificationType.GENERIC, "title-" + suffix, null);
        Notification aged = created.toBuilder().read(read).createdDate(createdDate).updatedDate(createdDate).build();
        return notificationRepository.update(aged);
    }
}
