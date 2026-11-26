package io.kestra.core.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.async.AsyncOperationProcessedEvent.Outcome;
import io.kestra.core.async.AsyncOperationProcessedEvent.Progress;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.lock.LockService;
import io.kestra.core.notification.model.NotificationItemOutcome;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.server.CoreAsyncOperationType;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.Disposable;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @BeforeEach
    void electTheSingletonAsLeader() {
        aggregator.start();
    }

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
    void shouldIgnoreProgressEventsAndRecordTerminalOutcomes() throws Exception {
        String operationId = TestsUtils.randomString(this.getClass().getSimpleName());
        notificationService.notifyAsyncOperation("test-user", null, operationId, CoreAsyncOperationType.EXECUTION_KILL, List.of("item-1", "item-2"));

        asyncOperationQueue.emit(new AsyncOperationProcessedEvent(operationId, null, "item-1", Outcome.RUNNING, null, new Progress(5, 10), Instant.now()));
        asyncOperationQueue.emit(new AsyncOperationProcessedEvent(operationId, null, "item-2", Outcome.FAILED, "boom", Instant.now()));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(
            () -> assertThat(notificationItemRepository.findResourceIds(null, operationId, NotificationItemOutcome.FAILED)).containsExactly("item-2")
        );
        assertThat(notificationItemRepository.findResourceIds(null, operationId, NotificationItemOutcome.PENDING)).containsExactly("item-1");
    }

    @Test
    void shouldProcessEventOnlyOnceWhenAnotherAggregatorInstanceCompetesForLeadership() throws Exception {
        // Simulates a second Executor instance in the cluster: it shares the same lock table and
        // queue, so at most one of the two can win the leader lock and subscribe.
        AsyncOperationAggregator competingInstance = new AsyncOperationAggregator(notificationService, asyncOperationQueue, lockService);
        try {
            competingInstance.start();

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
            competingInstance.stop();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotCompeteForLeadershipWhenStopped() {
        LockService mockedLockService = mock(LockService.class);
        AsyncOperationAggregator stoppedInstance = new AsyncOperationAggregator(notificationService, mock(BroadcastQueueInterface.class), mockedLockService);

        stoppedInstance.stop();
        stoppedInstance.start();

        verify(mockedLockService, never()).tryLock(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReleaseLeadershipWhenStopped() {
        AtomicInteger releases = new AtomicInteger();
        QueueSubscriber<AsyncOperationProcessedEvent> subscriber = mock(QueueSubscriber.class);
        AsyncOperationAggregator leader = electedLeader(Disposable.of(releases::incrementAndGet), subscriber);

        leader.stop();
        leader.stop();

        assertThat(releases.get()).isEqualTo(1);
        verify(subscriber).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotThrowWhenReleasingTheLeaderLockFails() {
        AsyncOperationAggregator leader = electedLeader(
            Disposable.of(() -> {
                throw new IllegalStateException("The lock table cannot be reached.");
            }),
            mock(QueueSubscriber.class)
        );

        assertThatCode(leader::stop).doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private AsyncOperationAggregator electedLeader(Disposable lock, QueueSubscriber<AsyncOperationProcessedEvent> subscriber) {
        LockService mockedLockService = mock(LockService.class);
        when(mockedLockService.tryLock(any(), any())).thenReturn(Optional.of(lock));
        BroadcastQueueInterface<AsyncOperationProcessedEvent> mockedQueue = mock(BroadcastQueueInterface.class);
        when(mockedQueue.subscriber()).thenReturn(subscriber);
        when(subscriber.subscribe(any())).thenReturn(subscriber);

        AsyncOperationAggregator leader = new AsyncOperationAggregator(notificationService, mockedQueue, mockedLockService);
        leader.start();
        return leader;
    }
}
