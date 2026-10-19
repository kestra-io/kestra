package io.kestra.core.notification;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.notification.NotificationItemRepositoryInterface.TenantOperationId;
import io.kestra.core.notification.model.NotificationItem;
import io.kestra.core.notification.model.NotificationItemOutcome;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
public abstract class AbstractNotificationItemRepositoryTest {
    @Inject
    private NotificationItemRepositoryInterface notificationItemRepository;

    private static NotificationItem item(String operationId, String resourceId, String tenantId) {
        return item(operationId, resourceId, tenantId, NotificationItemOutcome.PENDING);
    }

    private static NotificationItem item(String operationId, String resourceId, String tenantId, NotificationItemOutcome outcome) {
        return NotificationItem.builder()
            .operationId(operationId)
            .tenantId(tenantId)
            .resourceId(resourceId)
            .outcome(outcome)
            .updated(Instant.now())
            .build();
    }

    @Test
    void create() {
        String operationId = TestsUtils.randomString(this.getClass().getSimpleName());
        List<NotificationItem> created = notificationItemRepository.create(
            List.of(item(operationId, "res-1", "tenantA"), item(operationId, "res-2", "tenantA"))
        );

        assertThat(created).hasSize(2);
        assertThat(notificationItemRepository.countUpToDateOperationOutcomesByOperationId("tenantA", operationId).get(NotificationItemOutcome.PENDING)).isEqualTo(2L);
    }

    @Test
    void create_reinsertingSameNotificationAndResourceOverwritesRatherThanDuplicates() {
        String operationId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationItemRepository.create(List.of(item(operationId, "res-1", "tenantA")));
        notificationItemRepository.create(List.of(item(operationId, "res-1", "tenantA")));

        assertThat(notificationItemRepository.countUpToDateOperationOutcomesByOperationId("tenantA", operationId).get(NotificationItemOutcome.PENDING)).isEqualTo(1L);
    }

    @Test
    void create_upsertsOutcomeOfAnExistingRow() {
        String operationId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationItemRepository.create(List.of(item(operationId, "res-1", "tenantA")));

        notificationItemRepository.create(List.of(item(operationId, "res-1", "tenantA", NotificationItemOutcome.SUCCEEDED)));

        Map<NotificationItemOutcome, Long> counts = notificationItemRepository.countUpToDateOperationOutcomesByOperationId("tenantA", operationId);
        assertThat(counts.get(NotificationItemOutcome.SUCCEEDED)).isEqualTo(1L);
        assertThat(counts.getOrDefault(NotificationItemOutcome.PENDING, 0L)).isEqualTo(0L);
    }

    @Test
    void create_upsertsAPreviouslyUnknownRowRatherThanNoOp() {
        String operationId = TestsUtils.randomString(this.getClass().getSimpleName());

        notificationItemRepository.create(List.of(item(operationId, "unknown-resource", "tenantA", NotificationItemOutcome.FAILED)));

        assertThat(notificationItemRepository.countUpToDateOperationOutcomesByOperationId("tenantA", operationId).get(NotificationItemOutcome.FAILED)).isEqualTo(1L);
    }

    @Test
    void update_upsertsOutcomeOfAnExistingRow() {
        String operationId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationItemRepository.create(List.of(item(operationId, "res-1", "tenantA")));

        notificationItemRepository.update(item(operationId, "res-1", "tenantA", NotificationItemOutcome.SUCCEEDED));

        Map<NotificationItemOutcome, Long> counts = notificationItemRepository.countUpToDateOperationOutcomesByOperationId("tenantA", operationId);
        assertThat(counts.get(NotificationItemOutcome.SUCCEEDED)).isEqualTo(1L);
        assertThat(counts.getOrDefault(NotificationItemOutcome.PENDING, 0L)).isEqualTo(0L);
    }

    @Test
    void update_upsertsAPreviouslyUnknownRowRatherThanNoOp() {
        String operationId = TestsUtils.randomString(this.getClass().getSimpleName());

        notificationItemRepository.update(item(operationId, "unknown-resource", "tenantA", NotificationItemOutcome.FAILED));

        assertThat(notificationItemRepository.countUpToDateOperationOutcomesByOperationId("tenantA", operationId).get(NotificationItemOutcome.FAILED)).isEqualTo(1L);
    }

    @Test
    void countUpToDateOperationOutcomesByOperationId_talliesByOutcome() {
        String operationId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationItemRepository.create(
            List.of(
                item(operationId, "res-1", "tenantA", NotificationItemOutcome.SUCCEEDED),
                item(operationId, "res-2", "tenantA", NotificationItemOutcome.FAILED),
                item(operationId, "res-3", "tenantA")
            )
        );

        Map<NotificationItemOutcome, Long> counts = notificationItemRepository.countUpToDateOperationOutcomesByOperationId("tenantA", operationId);
        assertThat(counts.get(NotificationItemOutcome.SUCCEEDED)).isEqualTo(1L);
        assertThat(counts.get(NotificationItemOutcome.FAILED)).isEqualTo(1L);
        assertThat(counts.get(NotificationItemOutcome.PENDING)).isEqualTo(1L);
    }

    @Test
    void findResourceIds_returnsAllResourceIdsForOperation() {
        String operationId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationItemRepository.create(
            List.of(
                item(operationId, "res-1", "tenantA"),
                item(operationId, "res-2", "tenantA")
            )
        );

        assertThat(notificationItemRepository.findResourceIds("tenantA", operationId, null))
            .containsExactlyInAnyOrder("res-1", "res-2");
    }

    @Test
    void findResourceIds_narrowsToOutcome() {
        String operationId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationItemRepository.create(
            List.of(
                item(operationId, "res-1", "tenantA", NotificationItemOutcome.FAILED),
                item(operationId, "res-2", "tenantA", NotificationItemOutcome.SUCCEEDED)
            )
        );

        assertThat(notificationItemRepository.findResourceIds("tenantA", operationId, NotificationItemOutcome.FAILED))
            .containsExactly("res-1");
    }

    @Test
    void findResourceIds_unknownOperationIdReturnsEmpty() {
        assertThat(notificationItemRepository.findResourceIds("tenantA", "unknown-operation-id", null)).isEmpty();
    }

    @Test
    void deleteByOperationIds() {
        String keep = TestsUtils.randomString(this.getClass().getSimpleName());
        String purge1 = TestsUtils.randomString(this.getClass().getSimpleName());
        String purge2 = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationItemRepository.create(List.of(item(keep, "res-1", "tenantA")));
        notificationItemRepository.create(List.of(item(purge1, "res-1", "tenantA")));
        notificationItemRepository.create(List.of(item(purge2, "res-1", "tenantA")));

        int deleted = notificationItemRepository.deleteByOperationIds(
            List.of(
                new TenantOperationId("tenantA", purge1),
                new TenantOperationId("tenantA", purge2)
            )
        );

        assertThat(deleted).isEqualTo(2);
        assertThat(notificationItemRepository.countUpToDateOperationOutcomesByOperationId("tenantA", keep)).isNotEmpty();
        assertThat(notificationItemRepository.countUpToDateOperationOutcomesByOperationId("tenantA", purge1)).isEmpty();
        assertThat(notificationItemRepository.countUpToDateOperationOutcomesByOperationId("tenantA", purge2)).isEmpty();
    }

    @Test
    void deleteByOperationIds_emptyListIsNoOp() {
        assertThat(notificationItemRepository.deleteByOperationIds(List.of())).isEqualTo(0);
    }
}
