package io.kestra.core.notification;

import io.kestra.core.annotations.RequiresExecutor;
import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.lock.LockService;
import io.kestra.core.notification.model.NotificationItemOutcome;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.utils.Disposable;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Tallies the succeeded/failed outcome of every {@link AsyncOperationProcessedEvent} onto the
 * notification tracking its operation.
 * <p>
 * Runs on the Executor, or a standalone server (see {@link RequiresExecutor}). A cluster may run
 * several Executors and the queue broadcasts every event to all of them, so only the instance
 * holding the {@value #LOCK_CATEGORY} lock subscribes and processes events; the others retry on
 * every tick in case the leader dies, so one of them takes over once its lock is released.
 */
@Slf4j
@Singleton
@RequiresExecutor
public class AsyncOperationAggregator {

    static final String LOCK_CATEGORY = "async_operation_aggregator";
    static final String LOCK_ID = "leader";

    private final NotificationService notificationService;
    private final BroadcastQueueInterface<AsyncOperationProcessedEvent> asyncOperationQueue;
    private final LockService lockService;

    private volatile Disposable leaderLock;
    private volatile QueueSubscriber<AsyncOperationProcessedEvent> queueSubscriber;

    @Inject
    public AsyncOperationAggregator(
        NotificationService notificationService,
        BroadcastQueueInterface<AsyncOperationProcessedEvent> asyncOperationQueue,
        LockService lockService) {
        this.notificationService = notificationService;
        this.asyncOperationQueue = asyncOperationQueue;
        this.lockService = lockService;
    }

    @Scheduled(initialDelay = "0s", fixedDelay = "10s")
    void tryBecomeLeaderAndSubscribe() {
        if (queueSubscriber != null) {
            return;
        }

        leaderLock = lockService.tryLock(LOCK_CATEGORY, LOCK_ID).orElse(null);
        if (leaderLock != null) {
            log.info("Elected as leader for {}, subscribing to async operation events", AsyncOperationAggregator.class.getSimpleName());
            queueSubscriber = asyncOperationQueue.subscriber().subscribe(either ->
            {
                try {
                    if (either.isRight()) {
                        log.error("Unable to deserialize AsyncOperationProcessedEvent: {}", either.getRight().getMessage());
                        return;
                    }

                    AsyncOperationProcessedEvent event = either.getLeft();
                    if (log.isTraceEnabled()) {
                        log.trace("AsyncOperationProcessedEvent received: {}", event);
                    }
                    NotificationItemOutcome outcome = switch (event.outcome()) {
                        case SUCCEEDED -> NotificationItemOutcome.SUCCEEDED;
                        case FAILED -> NotificationItemOutcome.FAILED;
                    };
                    notificationService.updateNotificationItemOutcome(event.operationId(), event.tenantId(), event.itemId(), outcome);
                } catch (Exception exception) {
                    log.error("Error while processing AsyncOperationProcessedEvent", exception);
                }
            });
        }
    }

    @PreDestroy
    void shutdown() {
        if (queueSubscriber != null) {
            queueSubscriber.close();
        }
        if (leaderLock != null) {
            leaderLock.dispose();
        }
    }
}
