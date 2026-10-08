package io.kestra.queue.jdbc.client;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.event.Event;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.queue.QueueService;
import io.kestra.queue.jdbc.client.JdbcQueueClient.BroadcastRecord;
import io.kestra.queue.jdbc.client.JdbcQueueClient.BroadcastRef;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcBroadcastSubscriber<T extends Event> extends JdbcSubscriber<T> {
    private final boolean recheck;
    private final Duration recheckWindow;

    private Long maxOffset = null;

    // Exclusive lower bound of the rows re-read on each active poll: every row at or below it is older than the recheck window.
    private long lowWater;
    private final NavigableSet<Long> delivered = new TreeSet<>();
    private long lastDeliveryNanos;

    public JdbcBroadcastSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName,
        MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService) {
        this(cls, queueService, jdbcQueueClient, queueName, metricRegistry, ignoreExecutionService, false);
    }

    /**
     * @param recheck when {@code true}, a row committed after a higher offset was read is still delivered if it commits within the recheck window, which assumes synchronized clocks between nodes
     */
    public JdbcBroadcastSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName,
        MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService,
        boolean recheck) {
        super(cls, queueService, jdbcQueueClient, queueName, metricRegistry, ignoreExecutionService);

        this.recheck = recheck;
        this.recheckWindow = recheck ? jdbcQueueClient.getConfiguration().broadcastRecheckWindow() : null;
    }

    @Override
    protected Integer poll(Consumer<byte[]> messageConsumer) {
        if (recheck) {
            return pollWithRecheck(records -> records.forEach(record -> messageConsumer.accept(record.value())));
        }

        Pair<Integer, Long> result = this.jdbcQueueClient.subscribeBroadcast(this.queueName, maxOffset, messageConsumer);
        maxOffset = result.getRight();

        return result.getLeft();
    }

    @Override
    protected Integer pollBatch(Consumer<List<byte[]>> messageConsumer) {
        if (recheck) {
            return pollWithRecheck(records -> messageConsumer.accept(records.stream().map(BroadcastRecord::value).toList()));
        }

        Pair<Integer, Long> result = this.jdbcQueueClient.subscribeBroadcastBatch(this.queueName, maxOffset, messageConsumer);
        maxOffset = result.getRight();

        return result.getLeft();
    }

    @Override
    protected void init() {
        maxOffset = this.jdbcQueueClient.fetchMaxOffset(this.queueName);
        lowWater = maxOffset;
        delivered.clear();
        lastDeliveryNanos = recheck ? System.nanoTime() - recheckWindow.toNanos() - 1 : 0;

        this.markReady();
    }

    private int pollWithRecheck(Consumer<List<BroadcastRecord>> deliverer) {
        List<BroadcastRecord> toDeliver = new ArrayList<>(
            jdbcQueueClient.fetchBroadcast(queueName, maxOffset, jdbcQueueClient.getConfiguration().pollSize())
        );
        long newMaxOffset = toDeliver.isEmpty() ? maxOffset : toDeliver.getLast().offset();

        long now = System.nanoTime();
        boolean active = !toDeliver.isEmpty() || now - lastDeliveryNanos < recheckWindow.toNanos();

        List<BroadcastRef> window = List.of();
        if (active) {
            window = jdbcQueueClient.fetchBroadcastRefs(queueName, lowWater, newMaxOffset);

            Set<Long> known = new HashSet<>(delivered);
            toDeliver.forEach(record -> known.add(record.offset()));
            List<Long> missing = window.stream().map(BroadcastRef::offset).filter(offset -> !known.contains(offset)).toList();
            if (!missing.isEmpty()) {
                toDeliver.addAll(jdbcQueueClient.fetchBroadcastByOffsets(queueName, missing));
                toDeliver.sort(Comparator.comparingLong(BroadcastRecord::offset));
            }
        }

        if (!toDeliver.isEmpty()) {
            deliverer.accept(toDeliver);

            toDeliver.forEach(record -> delivered.add(record.offset()));
            lastDeliveryNanos = now;
        }
        maxOffset = newMaxOffset;

        if (active) {
            advanceLowWater(window);
        } else if (!delivered.isEmpty()) {
            lowWater = maxOffset;
            delivered.clear();
        }

        return toDeliver.size();
    }

    private void advanceLowWater(List<BroadcastRef> window) {
        Instant cutoff = Instant.now().minus(recheckWindow);
        long newLowWater = lowWater;
        for (BroadcastRef record : window) {
            if (record.created().isBefore(cutoff)) {
                newLowWater = Math.max(newLowWater, record.offset());
            }
        }

        if (newLowWater > lowWater) {
            lowWater = newLowWater;
            delivered.headSet(lowWater, true).clear();
        }
    }
}
