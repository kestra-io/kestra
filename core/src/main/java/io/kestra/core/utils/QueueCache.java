package io.kestra.core.utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.BroadcastEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * A cache backed by a queue.
 *
 * @param <T> the item of the cache
 */
@Slf4j
public class QueueCache<T extends SoftDeletable<T> & HasUID & BroadcastEvent> implements AutoCloseable {
    private final Map<String, T> cache;
    private final BroadcastQueueInterface<T> queue;
    private final Optional<Consumer<T>> invalidationListener;

    private QueueSubscriber<T> subscriber;

    /**
     * Create a cache backed by a queue.
     *
     * @see #QueueCache(BroadcastQueueInterface, Consumer)
     * @see #QueueCache(BroadcastQueueInterface, List)
     * @see #QueueCache(BroadcastQueueInterface, List, Consumer))
     */
    public QueueCache(BroadcastQueueInterface<T> queue) {
        this(queue, Collections.emptyList(), null);
    }

    /**
     * Create a cache backed by a queue with an invalidation listener.
     * The invalidation listener will be called whenever an item is removed or updated from the cache.
     *
     * @see #QueueCache(BroadcastQueueInterface)
     * @see #QueueCache(BroadcastQueueInterface, List)
     * @see #QueueCache(BroadcastQueueInterface, List, Consumer)
     */
    public QueueCache(BroadcastQueueInterface<T> queue, Consumer<T> invalidationListener) {
        this(queue, Collections.emptyList(), invalidationListener);
    }

    /**
     * Create a cache backed by a queue initialized with a list of items.
     *
     * @see #QueueCache(BroadcastQueueInterface)
     * @see #QueueCache(BroadcastQueueInterface, Consumer)
     * @see #QueueCache(BroadcastQueueInterface, List, Consumer)
     */
    public QueueCache(BroadcastQueueInterface<T> queue, List<T> initial) {
        this(queue, initial, null);
    }

    /**
     * Create a cache backed by a queue initialized with a list of items and an invalidation listener.
     * The invalidation listener will be called whenever an item is removed or updated from the cache.
     *
     * @see #QueueCache(BroadcastQueueInterface)
     * @see #QueueCache(BroadcastQueueInterface, Consumer)
     * @see #QueueCache(BroadcastQueueInterface, List)
     */
    public QueueCache(BroadcastQueueInterface<T> queue, List<T> initial, Consumer<T> invalidationListener) {
        this.queue = queue;
        this.cache = new ConcurrentHashMap<>(calculateHashMapCapacity(initial.size()));
        this.invalidationListener = Optional.ofNullable(invalidationListener);

        initial.forEach(it -> cache.put(it.uid(), it));

    }

    // this method is copied from HashMap.newHashMap() as the same didn't exist for ConcurrentHashMap
    // and modified to have a size of min 16
    private int calculateHashMapCapacity(int numMappings) {
        return Math.max(16, (int) Math.ceil(numMappings / 0.75f));
    }

    public void start() {
        // listen to item updates from the queue
        this.subscriber = queue.subscriber().subscribe(either ->
        {
            if (either.isRight()) {
                log.error("Unable to deserialize a message: {}", either.getRight().getMessage());
            } else {
                T item = either.getLeft();
                if (item.isDeleted()) {
                    cache.remove(item.uid());
                } else {
                    cache.put(item.uid(), item);
                }

                invalidationListener.ifPresent(listener -> listener.accept(item));
            }
        });
    }

    /**
     * Get an item from the cache.
     */
    public T get(String uid) {
        return cache.get(uid);
    }

    /**
     * Put an item in the cache.
     *
     * @see #putIfAbsent(T)
     */
    public void put(T item) {
        cache.put(item.uid(), item);
    }

    /**
     * Put an item in the cache if absent.
     *
     * @see #put(T)
     */
    public void putIfAbsent(T item) {
        cache.putIfAbsent(item.uid(), item);
    }

    /**
     * Clear the cache.
     * Should only be used for tests.
     */
    @VisibleForTesting
    public void clear() {
        cache.clear();
    }

    @Override
    public void close() {
        this.subscriber.close();
    }

    /**
     * Get all items from the cache.
     */
    public List<T> values() {
        return new ArrayList<>(cache.values());
    }
}
