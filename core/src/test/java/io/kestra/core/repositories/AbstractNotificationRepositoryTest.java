package io.kestra.core.repositories;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.notifications.CoreNotificationType;
import io.kestra.core.models.notifications.Notification;
import io.kestra.core.repositories.NotificationRepositoryInterface.NotificationCursor;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public abstract class AbstractNotificationRepositoryTest {
    @Inject
    private NotificationRepositoryInterface notificationRepository;

    private static Notification notification(String userId, String suffix, String tenantId, Instant createdDate, boolean read) {
        return Notification.builder()
            .id(userId + "-" + suffix)
            .userId(userId)
            .tenantId(tenantId)
            .type(CoreNotificationType.GENERIC.name())
            .title("title-" + suffix)
            .read(read)
            .createdDate(createdDate)
            .updatedDate(createdDate)
            .build();
    }

    @Test
    void create() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        Notification created = notificationRepository.create(notification(userId, "n1", null, Instant.now(), false));

        assertThat(created.getTitle()).isEqualTo("title-n1");
    }

    @Test
    void findById() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationRepository.create(notification(userId, "n1", null, Instant.now(), false));

        Optional<Notification> found = notificationRepository.findById(userId, userId + "-n1");
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("title-n1");

        assertThat(notificationRepository.findById(userId, userId + "-unknown")).isEmpty();
    }

    @Test
    void findByUserTypeAndReferenceId() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        Notification withRef = notification(userId, "n1", null, Instant.now(), false).toBuilder().referenceId("op-1").build();
        notificationRepository.create(withRef);

        Optional<Notification> found = notificationRepository.findByUserTypeAndReferenceId(userId, CoreNotificationType.GENERIC.name(), "op-1");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(userId + "-n1");

        assertThat(notificationRepository.findByUserTypeAndReferenceId(userId, CoreNotificationType.GENERIC.name(), "op-unknown")).isEmpty();
    }

    @Test
    void update() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        Notification created = notificationRepository.create(notification(userId, "n1", null, Instant.now(), false));

        Notification updated = notificationRepository.update(created.toBuilder().succeededItems(2).totalItems(5).build());
        assertThat(updated.getSucceededItems()).isEqualTo(2);

        Notification reloaded = notificationRepository.findById(userId, created.getId()).orElseThrow();
        assertThat(reloaded.getSucceededItems()).isEqualTo(2);
        assertThat(reloaded.getTotalItems()).isEqualTo(5);
    }

    @Test
    void markRead() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationRepository.create(notification(userId, "n1", null, Instant.now(), false));

        assertThat(notificationRepository.markRead(userId, userId + "-n1")).isTrue();
        assertThat(notificationRepository.findById(userId, userId + "-n1").orElseThrow().isRead()).isTrue();

        // unknown id: no-op, returns false
        assertThat(notificationRepository.markRead(userId, userId + "-unknown")).isFalse();
    }

    @Test
    void markUnread() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationRepository.create(notification(userId, "n1", null, Instant.now(), true));

        assertThat(notificationRepository.markUnread(userId, userId + "-n1")).isTrue();
        assertThat(notificationRepository.findById(userId, userId + "-n1").orElseThrow().isRead()).isFalse();

        // unknown id: no-op, returns false
        assertThat(notificationRepository.markUnread(userId, userId + "-unknown")).isFalse();
    }

    @Test
    void countUnread() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationRepository.create(notification(userId, "n1", null, Instant.now(), false));
        notificationRepository.create(notification(userId, "n2", null, Instant.now(), true));
        notificationRepository.create(notification(userId, "n3", null, Instant.now(), false));

        assertThat(notificationRepository.countUnread(userId, Set.of())).isEqualTo(2L);
    }

    @Test
    void markAllRead_onlyAffectsAccessibleTenants() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationRepository.create(notification(userId, "global", null, Instant.now(), false));
        notificationRepository.create(notification(userId, "accessible", "tenantA", Instant.now(), false));
        notificationRepository.create(notification(userId, "inaccessible", "tenantB", Instant.now(), false));

        List<Notification> updated = notificationRepository.markAllRead(userId, Set.of("tenantA"));
        assertThat(updated).hasSize(2);

        assertThat(notificationRepository.findById(userId, userId + "-global").orElseThrow().isRead()).isTrue();
        assertThat(notificationRepository.findById(userId, userId + "-accessible").orElseThrow().isRead()).isTrue();
        assertThat(notificationRepository.findById(userId, userId + "-inaccessible").orElseThrow().isRead()).isFalse();
    }

    @Test
    void findByUserSince_strictlyAfterBoundary() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        Instant since = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // exactly at the boundary: excluded
        notificationRepository.create(notification(userId, "atBoundary", null, since, false));
        // after the boundary: included
        notificationRepository.create(notification(userId, "afterBoundary", null, since.plusMillis(1000), false));
        // before the boundary: excluded
        notificationRepository.create(notification(userId, "beforeBoundary", null, since.minusSeconds(10), false));

        List<Notification> results = notificationRepository.findByUserSince(userId, Set.of(), since);

        assertThat(results)
            .extracting(Notification::getId)
            .containsExactly(userId + "-afterBoundary");
    }

    @Test
    void findByUser_crossTenantSecurity_excludesInaccessibleTenant() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationRepository.create(notification(userId, "global", null, Instant.now(), false));
        notificationRepository.create(notification(userId, "accessible", "tenantA", Instant.now(), false));
        notificationRepository.create(notification(userId, "inaccessible", "tenantB", Instant.now(), false));

        List<Notification> results = notificationRepository.findByUser(userId, Set.of("tenantA"), null, 10);

        assertThat(results)
            .extracting(Notification::getId)
            .containsExactlyInAnyOrder(userId + "-global", userId + "-accessible");

        assertThat(notificationRepository.countUnread(userId, Set.of("tenantA"))).isEqualTo(2L);
    }

    @Test
    void findByUser_cursorPagination_walksAllPagesNewestFirst() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        for (int i = 1; i <= 5; i++) {
            notificationRepository.create(notification(userId, "n" + i, null, base.plusSeconds(i), false));
        }

        // page 1: n5, n4 (newest first)
        List<Notification> page1 = notificationRepository.findByUser(userId, Set.of(), null, 2);
        assertThat(page1).extracting(Notification::getId).containsExactly(userId + "-n5", userId + "-n4");

        // page 2: n3, n2
        Notification last = page1.get(page1.size() - 1);
        List<Notification> page2 = notificationRepository.findByUser(userId, Set.of(), new NotificationCursor(last.getCreatedDate(), last.getId()), 2);
        assertThat(page2).extracting(Notification::getId).containsExactly(userId + "-n3", userId + "-n2");

        // page 3: n1 (last page, fewer than limit)
        last = page2.get(page2.size() - 1);
        List<Notification> page3 = notificationRepository.findByUser(userId, Set.of(), new NotificationCursor(last.getCreatedDate(), last.getId()), 2);
        assertThat(page3).extracting(Notification::getId).containsExactly(userId + "-n1");
    }

    @Test
    void findByUser_cursorPagination_noSkipOrDuplicateAcrossConcurrentPurgeAndInsert() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // oldest -> newest: keep (unread, must survive & appear in page 2), purgeTarget (read, will be
        // purged between the two fetches), then two rows returned by page 1.
        notificationRepository.create(notification(userId, "keep", null, base.plusSeconds(1), false));
        Notification purgeTarget = notificationRepository.create(notification(userId, "purgeTarget", null, base.plusSeconds(2), true));
        notificationRepository.create(notification(userId, "p1b", null, base.plusSeconds(3), false));
        notificationRepository.create(notification(userId, "p1a", null, base.plusSeconds(4), false));

        List<Notification> page1 = notificationRepository.findByUser(userId, Set.of(), null, 2);
        assertThat(page1).extracting(Notification::getId).containsExactly(userId + "-p1a", userId + "-p1b");
        Notification cursorRow = page1.get(page1.size() - 1);

        // between the two fetches: the retention job purges the read row sitting between "keep" and
        // the cursor, and a brand new (much newer) row is inserted — neither should affect page 2.
        notificationRepository.deleteByQuery(purgeTarget.getUpdatedDate().plusSeconds(1), Instant.EPOCH);
        notificationRepository.create(notification(userId, "freshInsert", null, base.plusSeconds(100), false));

        // page 2 must contain exactly "keep" — not skip it because purgeTarget was deleted in between,
        // and not duplicate/leak the newly inserted, newer row.
        List<Notification> page2 = notificationRepository.findByUser(userId, Set.of(), new NotificationCursor(cursorRow.getCreatedDate(), cursorRow.getId()), 2);
        assertThat(page2).extracting(Notification::getId).containsExactly(userId + "-keep");
    }

    @Test
    void deleteByQuery_flatRetentionTtl() {
        String userId = TestsUtils.randomString(this.getClass().getSimpleName());
        Instant now = Instant.now();

        notificationRepository.create(notification(userId, "readOld", null, now.minus(10, ChronoUnit.DAYS), true));
        notificationRepository.create(notification(userId, "readRecent", null, now.minus(1, ChronoUnit.DAYS), true));
        notificationRepository.create(notification(userId, "unreadOld", null, now.minus(31, ChronoUnit.DAYS), false));
        notificationRepository.create(notification(userId, "unreadRecent", null, now.minus(1, ChronoUnit.DAYS), false));

        int deleted = notificationRepository.deleteByQuery(now.minus(7, ChronoUnit.DAYS), now.minus(30, ChronoUnit.DAYS));
        assertThat(deleted).isEqualTo(2);

        assertThat(notificationRepository.findById(userId, userId + "-readOld")).isEmpty();
        assertThat(notificationRepository.findById(userId, userId + "-unreadOld")).isEmpty();
        assertThat(notificationRepository.findById(userId, userId + "-readRecent")).isPresent();
        assertThat(notificationRepository.findById(userId, userId + "-unreadRecent")).isPresent();
    }
}
