package io.kestra.core.async;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.notification.NotificationItemRepositoryInterface;
import io.kestra.core.notification.NotificationService;
import io.kestra.core.notification.model.NotificationItem;
import io.kestra.core.notification.model.NotificationItemOutcome;
import io.kestra.core.server.CoreAsyncOperationType;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@KestraTest
class AsyncOperationResourceReleaserTest {
    @Inject
    private AsyncOperationResourceReleaser releaser;

    @Inject
    private NotificationService notificationService;

    @Inject
    private NotificationItemRepositoryInterface notificationItemRepository;

    @Test
    void shouldFailOnlyThePendingItemsOfTheReleasedService() {
        String deadService = TestsUtils.randomString(this.getClass().getSimpleName());
        String deadOperation = TestsUtils.randomString(this.getClass().getSimpleName());
        String liveOperation = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationService.notifyAsyncOperation("test-user", null, deadOperation, CoreAsyncOperationType.EXECUTION_KILL, List.of("item-1", "item-2"), deadService);
        notificationService.notifyAsyncOperation("test-user", null, liveOperation, CoreAsyncOperationType.EXECUTION_KILL, List.of("item-3"), "live-service");

        releaser.releaseResources(webserver(deadService), "service disconnected");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(
            () -> assertThat(notificationItemRepository.findByOperationId(null, deadOperation))
                .allSatisfy(item ->
                {
                    assertThat(item.getOutcome()).isEqualTo(NotificationItemOutcome.FAILED);
                    assertThat(item.getError()).contains(deadService, "service disconnected");
                })
        );
        assertThat(notificationItemRepository.findByOperationId(null, liveOperation))
            .extracting(NotificationItem::getOutcome)
            .containsExactly(NotificationItemOutcome.PENDING);
    }

    private static ServiceInstance webserver(String id) {
        Instant now = Instant.now();
        return ServiceInstance.create(id, ServiceType.WEBSERVER, null, now, now, null, Map.of(), Set.of());
    }
}
