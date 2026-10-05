package io.kestra.core.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.async.AsyncOperationProcessedEvent.Outcome;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.lock.LockService;
import io.kestra.core.notification.model.NotificationItemOutcome;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.server.CoreAsyncOperationType;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@KestraTest
class AsyncOperationAggregatorTest {

    @Inject
    private AsyncOperationAggregator aggregator;

    @Inject
    private BroadcastQueueInterface<AsyncOperationProcessedEvent> asyncOperationQueue;

    @Inject
    private NotificationService notificationService;

    @Inject
    private NotificationRepositoryInterface notificationRepository;

    @Inject
    private NotificationItemRepositoryInterface notificationItemRepository;

    @Inject
    private LockService lockService;

    @Test
    void shouldFlipItemOutcomesWhenProcessedEventsArrive() throws Exception {
        assertThat(aggregator).isNotNull();

        String operationId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationService.notifyAsyncOperation("test-user", TenantService.MAIN_TENANT, operationId, CoreAsyncOperationType.EXECUTION_KILL, List.of("item-1", "item-2", "item-3"));

        asyncOperationQueue.emit(new AsyncOperationProcessedEvent(operationId, TenantService.MAIN_TENANT, "item-1", Outcome.SUCCEEDED, null, Instant.now()));
        asyncOperationQueue.emit(new AsyncOperationProcessedEvent(operationId, TenantService.MAIN_TENANT, "item-2", Outcome.SUCCEEDED, null, Instant.now()));
        asyncOperationQueue.emit(new AsyncOperationProcessedEvent(operationId, TenantService.MAIN_TENANT, "item-3", Outcome.FAILED, "boom", Instant.now()));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
        {
            assertThat(notificationRepository.findByOperationId(operationId)).isPresent();
            Map<NotificationItemOutcome, Long> counts = notificationItemRepository.countUpToDateOperationOutcomesByOperationId(TenantService.MAIN_TENANT, operationId);
            assertThat(counts.get(NotificationItemOutcome.SUCCEEDED)).isEqualTo(2L);
            assertThat(counts.get(NotificationItemOutcome.FAILED)).isEqualTo(1L);
        });
    }

    @Test
    void shouldProcessEventOnlyOnceWhenAnotherAggregatorInstanceCompetesForLeadership() throws Exception {
        // Simulates a second Executor instance in the cluster: it shares the same lock table and
        // queue, so at most one of the two can win the leader lock and subscribe.
        AsyncOperationAggregator competingInstance = new AsyncOperationAggregator(notificationService, asyncOperationQueue, lockService);
        try {
            competingInstance.tryBecomeLeaderAndSubscribe();

            String operationId = TestsUtils.randomString(this.getClass().getSimpleName());
            notificationService.notifyAsyncOperation("test-user", TenantService.MAIN_TENANT, operationId, CoreAsyncOperationType.EXECUTION_KILL, List.of("item-1"));

            asyncOperationQueue.emit(new AsyncOperationProcessedEvent(operationId, TenantService.MAIN_TENANT, "item-1", Outcome.SUCCEEDED, null, Instant.now()));

            // pollDelay gives a losing subscriber time to (wrongly) double-process before asserting
            // the outcome settled correctly.
            await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            {
                assertThat(notificationRepository.findByOperationId(operationId)).isPresent();
                Map<NotificationItemOutcome, Long> counts = notificationItemRepository.countUpToDateOperationOutcomesByOperationId(TenantService.MAIN_TENANT, operationId);
                assertThat(counts.get(NotificationItemOutcome.SUCCEEDED)).isEqualTo(1L);
            });
        } finally {
            competingInstance.shutdown();
            // Restore the shared singleton as leader so later tests are not left without a subscriber.
            aggregator.tryBecomeLeaderAndSubscribe();
        }
    }
}
