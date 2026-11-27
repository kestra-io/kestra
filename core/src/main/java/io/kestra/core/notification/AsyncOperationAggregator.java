package io.kestra.core.notification;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.lock.LockService;
import io.kestra.core.notification.model.NotificationItemOutcome;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.utils.Disposable;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Tallies the succeeded/failed outcome of every terminal {@link AsyncOperationProcessedEvent} onto the
 * notification tracking its operation; progress events are not persisted.
 * <p>
 * Driven by the Executor, which calls {@link #start()} periodically while it runs
 * and {@link #stop()} once it has drained. A cluster may run several Executors and the queue broadcasts
 * every event to all of them, so only the instance holding the {@value #LOCK_CATEGORY} lock subscribes
 * and processes events; the others keep retrying in case the leader dies.
 */
@Slf4j
@Singleton
public class AsyncOperationAggregator {

    static final String LOCK_CATEGORY = "async_operation_aggregator";
    static final String LOCK_ID = "leader";

    private final NotificationService notificationService;
    private final BroadcastQueueInterface<AsyncOperationProcessedEvent> asyncOperationQueue;
    private final LockService lockService;

    private volatile Disposable leaderLock;
    private volatile QueueSubscriber<AsyncOperationProcessedEvent> queueSubscriber;
    private boolean stopped;

    @Inject
    public AsyncOperationAggregator(
        NotificationService notificationService,
        BroadcastQueueInterface<AsyncOperationProcessedEvent> asyncOperationQueue,
        LockService lockService) {
        this.notificationService = notificationService;
        this.asyncOperationQueue = asyncOperationQueue;
        this.lockService = lockService;
    }

    /**
     * Subscribes to the async operation events if this instance wins the leader lock. A no-op when it is
     * already the leader or has been stopped.
     */
    public synchronized void start() {
        if (stopped || queueSubscriber != null) {
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
                        case RUNNING -> null;
                        case SUCCEEDED -> NotificationItemOutcome.SUCCEEDED;
                        case FAILED -> NotificationItemOutcome.FAILED;
                    };
                    if (outcome != null) {
                        notificationService.updateNotificationItemOutcome(event.operationId(), event.tenantId(), event.itemId(), outcome);
                    }
                } catch (Exception exception) {
                    log.error("Error while processing AsyncOperationProcessedEvent", exception);
                }
            });
        }
    }

    /**
     * Closes the subscription and releases the leader lock, and keeps this instance from competing for
     * leadership again. Idempotent; a failure to release the lock is logged rather than thrown. Also runs
     * on bean destruction, for contexts where no Executor stopped it, such as tests.
     */
    @PreDestroy
    public synchronized void stop() {
        stopped = true;
        QueueSubscriber<AsyncOperationProcessedEvent> subscriber = queueSubscriber;
        queueSubscriber = null;
        Disposable lock = leaderLock;
        leaderLock = null;
        try {
            if (subscriber != null) {
                subscriber.close();
            }
        } finally {
            if (lock != null) {
                try {
                    lock.dispose();
                } catch (RuntimeException e) {
                    log.warn("Unable to release the {} leadership", AsyncOperationAggregator.class.getSimpleName(), e);
                }
            }
        }
    }
}
