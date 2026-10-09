package io.kestra.core.notification;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.notification.model.Notification;
import io.kestra.core.notification.model.NotificationEvent;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.MapUtils;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.FluxSink;

/**
 * Fan-out service for {@link NotificationEvent}: dispatches every created/updated {@link Notification}
 * to the subscribers currently following its {@code userId}.
 * <p>
 * Mirrors {@link io.kestra.core.services.ExecutionStreamingService}: subscribers are held in a nested
 * {@link ConcurrentHashMap} guarded by a single lock for add/remove atomicity, and the queue
 * subscription is paused while no one is listening.
 */
@Slf4j
@Singleton
public class NotificationStreamingService {
    private final Map<String, Map<String, Subscriber>> subscribers = new ConcurrentHashMap<>();
    private final Object subscriberLock = new Object();

    /**
     * {@link ConcurrentHashMap} rejects a {@code null} key, but OSS's single implicit user
     * genuinely has a {@code null} id. Translate it to this sentinel only for indexing the
     * in-memory subscriber map — every public parameter here, and the persisted
     * {@code Notification.userId}, stay the real nullable value.
     */
    private static final String NULL_USER_KEY = "default";

    private static String key(@Nullable String userId) {
        return userId == null ? NULL_USER_KEY : userId;
    }

    private final BroadcastQueueInterface<NotificationEvent> notificationQueue;
    private final NotificationRepositoryInterface notificationRepository;

    private QueueSubscriber<NotificationEvent> queueSubscriber;

    @Inject
    public NotificationStreamingService(BroadcastQueueInterface<NotificationEvent> notificationQueue, NotificationRepositoryInterface notificationRepository) {
        this.notificationQueue = notificationQueue;
        this.notificationRepository = notificationRepository;
    }

    @PostConstruct
    void startQueueConsumer() {
        // Single queue consumer, paused until the first subscriber registers.
        this.queueSubscriber = notificationQueue.subscriber();
        this.queueSubscriber.pause();
        this.queueSubscriber.subscribe(this::dispatch);
    }

    /**
     * Dispatch an event to all the subscribers of its user, resolving the owning {@code userId} by
     * looking up the notification the event refers to (the event itself only carries ids, not the
     * full notification).
     * This method never throws: the queue subscriber is shared by all subscribers and treats any escaping
     * exception as fatal, so a delivery failure to a single SSE stream would shut down the whole server.
     */
    private void dispatch(Either<NotificationEvent, DeserializationException> either) {
        try {
            if (either.isRight()) {
                log.error("Unable to deserialize notification event: {}", either.getRight().getMessage());
                return;
            }

            if (subscribers.isEmpty()) {
                return;
            }

            NotificationEvent event = either.getLeft();
            notificationRepository.findByNotificationIdOrReferenceId(event.notificationId(), event.referenceId())
                .ifPresent(notification -> dispatchToUser(notification.getUserId(), event));
        } catch (Exception e) {
            log.error("Unable to dispatch the notification event to its subscribers", e);
        }
    }

    private void dispatchToUser(@Nullable String userId, NotificationEvent event) {
        Map<String, Subscriber> userSubscribers = subscribers.get(key(userId));
        if (MapUtils.isEmpty(userSubscribers)) {
            return;
        }

        userSubscribers.forEach((subscriberId, subscriber) -> deliver(userId, subscriberId, subscriber, event));
    }

    /**
     * Deliver a notification update to a single subscriber, skipping it if the notification belongs to
     * a tenant the subscriber isn't accessible to (a notification with no tenant is always delivered).
     * This method never throws so a stale or broken SSE stream cannot prevent delivery to the other subscribers.
     */
    private void deliver(@Nullable String userId, String subscriberId, Subscriber subscriber, NotificationEvent event) {
        if (subscriber.sink().isCancelled()) {
            unregisterSubscriber(userId, subscriberId);
            return;
        }

        try {
            subscriber.sink().next(event);
        } catch (Exception e) {
            log.error("Error sending notification event to the subscriber '{}'", subscriberId, e);
            failSilently(subscriber.sink(), e);
            unregisterSubscriber(userId, subscriberId);
        }
    }

    private void failSilently(FluxSink<NotificationEvent> sink, Exception cause) {
        try {
            sink.error(cause);
        } catch (Exception e) {
            log.debug("Unable to fail an already terminated sink", e);
        }
    }

    /**
     * Register a subscriber for all of {@code userId}'s notifications, restricted to
     * {@code accessibleTenantIds} (a notification with no tenant is always delivered).
     * All subscribers must ensure to call {@link #unregisterSubscriber(String, String)} to avoid any memory leak.
     */
    public void registerSubscriber(@Nullable String userId, String subscriberId, FluxSink<NotificationEvent> sink) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            subscribers.computeIfAbsent(key(userId), k -> new ConcurrentHashMap<>())
                .put(subscriberId, new Subscriber(sink));

            if (this.queueSubscriber.isPaused()) {
                this.queueSubscriber.resume();
            }
        }
    }

    /**
     * Unregister a subscriber.
     * This is advised to do it in a finally block to be sure to free resources.
     */
    public void unregisterSubscriber(@Nullable String userId, String subscriberId) {
        // it needs to be synchronized as we get and remove if empty, so we must be sure that nobody else is adding a new one in-between
        synchronized (subscriberLock) {
            String key = key(userId);
            Map<String, Subscriber> userSubscribers = subscribers.get(key);
            if (userSubscribers != null) {
                userSubscribers.remove(subscriberId);
                if (userSubscribers.isEmpty()) {
                    subscribers.remove(key);
                }
            }

            // pause the subscription if no one is listening anymore
            if (MapUtils.isEmpty(subscribers) && !this.queueSubscriber.isPaused()) {
                this.queueSubscriber.pause();
            }
        }
    }

    @PreDestroy
    void shutdown() {
        if (queueSubscriber != null) {
            queueSubscriber.close();
        }
    }

    private record Subscriber(FluxSink<NotificationEvent> sink) {
    }
}
