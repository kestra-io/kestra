package io.kestra.queue.jdbc.client;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueService;
import io.kestra.core.queues.event.Event;
import io.kestra.core.services.IgnoreExecutionService;

import lombok.extern.slf4j.Slf4j;

/** Also re-reads the offsets of the last {@code lateCommitWindow} not yet delivered, as a message can commit after a higher offset was polled. */
@Slf4j
public class JdbcBroadcastSubscriber<T extends Event> extends JdbcSubscriber<T> {
    // bounds the bitset to 4 MB: an offset further behind the head is given up, which inside the window would need over a million rows per second
    private static final long MAX_TRACKED_OFFSETS = 1L << 25;

    private final long lateCommitWindowNanos;
    private final long recheckIntervalNanos;

    private Long maxOffset = null;
    // every offset up to this one was either delivered or has been missing for longer than the late commit window
    private long floorOffset = 0L;
    // bit (offset - floorOffset) is set once that offset was delivered
    private BitSet delivered = new BitSet();
    // the floor is advanced to the max offset of a mark once the mark is older than the window
    private final Deque<PolledMark> polledMarks = new ArrayDeque<>();
    private long lastRecheck = System.nanoTime();

    public JdbcBroadcastSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName,
        MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService) {
        super(cls, queueService, jdbcQueueClient, queueName, metricRegistry, ignoreExecutionService);

        this.lateCommitWindowNanos = jdbcQueueClient.getConfiguration().lateCommitWindow().toNanos();
        this.recheckIntervalNanos = jdbcQueueClient.getConfiguration().lateCommitRecheckInterval().toNanos();
    }

    @Override
    protected Integer poll(Consumer<byte[]> messageConsumer) {
        return pollBatch(messages -> messages.forEach(messageConsumer));
    }

    @Override
    protected Integer pollBatch(Consumer<List<byte[]>> messageConsumer) {
        List<Long> offsets = this.jdbcQueueClient.subscribeBroadcastBatch(this.queueName, maxOffset, messageConsumer);
        if (!offsets.isEmpty()) {
            offsets.forEach(this::markDelivered);
            maxOffset = offsets.getLast();
        }

        return offsets.size() + deliverLateCommits(messageConsumer);
    }

    @Override
    protected void init() {
        maxOffset = this.jdbcQueueClient.fetchMaxOffset(this.queueName);
        floorOffset = maxOffset;
        delivered = new BitSet();
        polledMarks.clear();

        this.markReady();
    }

    private void markDelivered(long offset) {
        if (offset - floorOffset > MAX_TRACKED_OFFSETS) {
            rebase(offset - MAX_TRACKED_OFFSETS);
        }
        delivered.set(Math.toIntExact(offset - floorOffset));
    }

    private boolean isDelivered(long offset) {
        return delivered.get(Math.toIntExact(offset - floorOffset));
    }

    private int deliverLateCommits(Consumer<List<byte[]>> messageConsumer) {
        long now = System.nanoTime();
        if (now - lastRecheck < recheckIntervalNanos) {
            return 0;
        }
        lastRecheck = now;

        if (polledMarks.isEmpty() || polledMarks.getLast().maxOffset() != maxOffset) {
            polledMarks.addLast(new PolledMark(now, maxOffset));
        }
        advanceFloor(now);
        if (maxOffset <= floorOffset) {
            return 0;
        }

        List<Long> missing = this.jdbcQueueClient.fetchBroadcastOffsets(this.queueName, floorOffset, maxOffset).stream()
            .filter(offset -> !isDelivered(offset))
            .toList();
        if (missing.isEmpty()) {
            return 0;
        }

        List<Long> lateOffsets = this.jdbcQueueClient.subscribeBroadcastByOffsets(this.queueName, missing, messageConsumer);
        if (log.isDebugEnabled()) {
            log.debug("[{}] delivered {} message(s) committed after a higher offset was polled", this.queueName, lateOffsets.size());
        }
        lateOffsets.forEach(this::markDelivered);

        return lateOffsets.size();
    }

    private void advanceFloor(long now) {
        long newFloor = floorOffset;
        while (!polledMarks.isEmpty() && now - polledMarks.getFirst().nanoTime() > lateCommitWindowNanos) {
            newFloor = Math.max(newFloor, polledMarks.removeFirst().maxOffset());
        }
        if (newFloor > floorOffset) {
            rebase(newFloor);
        }
    }

    private void rebase(long newFloor) {
        long shift = newFloor - floorOffset;
        delivered = shift < delivered.length() ? delivered.get((int) shift, delivered.length()) : new BitSet();
        floorOffset = newFloor;
    }

    private record PolledMark(long nanoTime, long maxOffset) {
    }
}
