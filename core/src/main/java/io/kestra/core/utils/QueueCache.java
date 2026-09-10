package io.kestra.core.utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    /**
     * Set only by the lazy constructors. When present, the initial load and the queue subscription
     * (both of which read the database) are deferred to the first real read instead of running from
     * the constructor and {@link #start()}.
     */
    private final Supplier<List<T>> lazyInitialSupplier;
    private volatile boolean lazyStarted = false;

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
        this.lazyInitialSupplier = null;

        initial.forEach(it -> cache.put(it.uid(), it));
    }

    /**
     * Create a cache backed by a queue whose initial load is deferred until the cache is first read.
     * <p>
     * Unlike the {@link List}-based constructors, neither the constructor nor {@link #start()} reads
     * the database: the supplier runs, and the queue is subscribed to, on the first call to
     * {@link #get(String)} or {@link #values()}. Use this when the cache may be constructed — for
     * instance because a {@code StartupEvent} listener forces it, regardless of whether anything
     * reads it — in a context whose database is not necessarily migrated or reachable yet.
     *
     * @see #QueueCache(BroadcastQueueInterface, Supplier, Consumer)
     */
    public QueueCache(BroadcastQueueInterface<T> queue, Supplier<List<T>> initialSupplier) {
        this(queue, initialSupplier, null);
    }

    /**
     * Create a cache backed by a queue whose initial load is deferred until the cache is first read,
     * with an invalidation listener. The invalidation listener will be called whenever an item is
     * removed or updated from the cache.
     *
     * @see #QueueCache(BroadcastQueueInterface, Supplier)
     */
    public QueueCache(BroadcastQueueInterface<T> queue, Supplier<List<T>> initialSupplier, Consumer<T> invalidationListener) {
        this.queue = queue;
        this.cache = new ConcurrentHashMap<>(16);
        this.invalidationListener = Optional.ofNullable(invalidationListener);
        this.lazyInitialSupplier = Objects.requireNonNull(initialSupplier);
    }

    // this method is copied from HashMap.newHashMap() as the same didn't exist for ConcurrentHashMap
    // and modified to have a size of min 16
    private int calculateHashMapCapacity(int numMappings) {
        return Math.max(16, (int) Math.ceil(numMappings / 0.75f));
    }

    /**
     * Subscribes to the queue immediately. A no-op for a lazily-constructed cache, which subscribes
     * on first read instead — see {@link #QueueCache(BroadcastQueueInterface, Supplier)}.
     */
    public void start() {
        if (lazyInitialSupplier == null) {
            subscribe();
        }
    }

    private void ensureLazyStarted() {
        if (lazyInitialSupplier == null || lazyStarted) {
            return;
        }
        synchronized (this) {
            if (!lazyStarted) {
                lazyInitialSupplier.get().forEach(it -> cache.put(it.uid(), it));
                subscribe();
                lazyStarted = true;
            }
        }
    }

    private void subscribe() {
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
        ensureLazyStarted();
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
        if (this.subscriber != null) {
            this.subscriber.close();
        }
    }

    /**
     * Get all items from the cache.
     */
    public List<T> values() {
        ensureLazyStarted();
        return new ArrayList<>(cache.values());
    }
}
