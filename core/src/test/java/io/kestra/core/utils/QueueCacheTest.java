package io.kestra.core.utils;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.queues.event.BroadcastEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QueueCacheTest {
    private record Item(String uid, boolean deleted) implements SoftDeletable<Item>, HasUID, BroadcastEvent {
        @Override
        public boolean isDeleted() {
            return deleted;
        }

        @Override
        public Item toDeleted() {
            return new Item(uid, true);
        }

        @Override
        public String key() {
            return uid;
        }
    }

    @SuppressWarnings("unchecked")
    private BroadcastQueueInterface<Item> mockQueue() {
        BroadcastQueueInterface<Item> queue = mock(BroadcastQueueInterface.class);
        QueueSubscriber<Item> subscriber = mock(QueueSubscriber.class);
        when(queue.subscriber()).thenReturn(subscriber);
        when(subscriber.subscribe(any())).thenReturn(subscriber);
        return queue;
    }

    @Test
    void shouldPopulateImmediatelyWithTheEagerConstructor() {
        BroadcastQueueInterface<Item> queue = mockQueue();

        QueueCache<Item> cache = new QueueCache<>(queue, List.of(new Item("a", false)));

        assertThat(cache.get("a")).isNotNull();
        // start() is required to subscribe for the eager flavour: not called yet.
        Mockito.verify(queue, Mockito.never()).subscriber();
    }

    @Test
    void shouldNotCallTheSupplierUntilFirstReadWithTheLazyConstructor() {
        BroadcastQueueInterface<Item> queue = mockQueue();
        AtomicInteger callCount = new AtomicInteger();

        QueueCache<Item> cache = new QueueCache<>(queue, () -> {
            callCount.incrementAndGet();
            return List.of(new Item("a", false));
        });

        assertThat(callCount).hasValue(0);
        verifyNoInteractions(queue);

        assertThat(cache.get("a")).isNotNull();

        assertThat(callCount).hasValue(1);
        Mockito.verify(queue, Mockito.times(1)).subscriber();
    }

    @Test
    void shouldOnlyCallTheSupplierOnceAcrossMultipleReads() {
        BroadcastQueueInterface<Item> queue = mockQueue();
        AtomicInteger callCount = new AtomicInteger();

        QueueCache<Item> cache = new QueueCache<>(queue, () -> {
            callCount.incrementAndGet();
            return List.of(new Item("a", false));
        });

        cache.get("a");
        cache.values();
        cache.get("a");

        assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldTreatStartAsANoOpForTheLazyConstructor() {
        BroadcastQueueInterface<Item> queue = mockQueue();
        AtomicInteger callCount = new AtomicInteger();

        QueueCache<Item> cache = new QueueCache<>(queue, () -> {
            callCount.incrementAndGet();
            return List.of();
        });

        cache.start();

        assertThat(callCount).hasValue(0);
        verifyNoInteractions(queue);
    }

    @Test
    void shouldNotThrowOnCloseWhenNeverStarted() {
        BroadcastQueueInterface<Item> queue = mockQueue();
        Consumer<Item> invalidationListener = item -> { };

        QueueCache<Item> cache = new QueueCache<>(queue, List::of, invalidationListener);

        cache.close();
    }
}
