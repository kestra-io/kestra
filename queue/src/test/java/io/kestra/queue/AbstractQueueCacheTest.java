package io.kestra.queue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.QueueCache;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractQueueCacheTest {

    @Inject
    private BroadcastQueueInterface<DeletableBroadcastTestEvent> testQueue;

    @Test
    void noInitialValues() throws InterruptedException, QueueException {
        var queueCache = new QueueCache<>(testQueue);
        queueCache.start();

        List<DeletableBroadcastTestEvent> values = queueCache.values();

        assertThat(values).isEmpty();

        var event = new DeletableBroadcastTestEvent(IdUtils.create(), IdUtils.create(), false);
        testQueue.emit(event);
        Thread.sleep(100); // make sure it receives the new flow

        values = queueCache.values();

        assertThat(values).hasSize(1);

        var result = queueCache.get(event.uid());
        assertThat(result).isNotNull();
        assertThat(result.uid()).isEqualTo(event.uid());

        queueCache.putIfAbsent(event);
        values = queueCache.values();
        assertThat(values).hasSize(1);

        queueCache.put(new DeletableBroadcastTestEvent(IdUtils.create(), IdUtils.create(), false));
        values = queueCache.values();
        assertThat(values).hasSize(2);

        queueCache.close();
    }

    @Test
    void withInitialValues() throws QueueException, InterruptedException {
        List<DeletableBroadcastTestEvent> events = List.of(
            new DeletableBroadcastTestEvent(IdUtils.create(), IdUtils.create(), false),
            new DeletableBroadcastTestEvent(IdUtils.create(), IdUtils.create(), false)
        );
        var queueCache = new QueueCache<>(testQueue, events);
        queueCache.start();

        List<DeletableBroadcastTestEvent> values = queueCache.values();

        assertThat(values).hasSize(2);

        var event = new DeletableBroadcastTestEvent(IdUtils.create(), IdUtils.create(), false);
        testQueue.emit(event);

        // we need to await at least the Kafka poll interval which should be 500ms by default
        values = await().atMost(Duration.ofSeconds(1)).until(
            () -> queueCache.values(),
            it -> it.size() == 3
        );

        assertThat(values).hasSize(3);

        var result = queueCache.get(event.uid());
        assertThat(result).isNotNull();
        assertThat(result.uid()).isEqualTo(event.uid());

        queueCache.close();
    }

    public record DeletableBroadcastTestEvent(String key, String uid, boolean deleted) implements SoftDeletable<DeletableBroadcastTestEvent>, HasUID, BroadcastEvent {
        @Override
        public boolean isDeleted() {
            return deleted;
        }

        @Override
        public DeletableBroadcastTestEvent toDeleted() {
            return new DeletableBroadcastTestEvent(key, uid, true);
        }
    }
}