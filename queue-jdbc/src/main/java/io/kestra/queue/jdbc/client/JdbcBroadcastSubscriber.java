package io.kestra.queue.jdbc.client;

import java.util.*;
import java.util.function.Consumer;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueService;
import io.kestra.core.queues.event.Event;
import io.kestra.core.services.IgnoreExecutionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JdbcBroadcastSubscriber<T extends Event> extends JdbcSubscriber<T> {
    private static final int MAX_GAPS_PER_QUERY = 50;
    private static final int MAX_PENDING_GAPS = 1000;

    private Long maxOffset = null;
    private final List<GapRange> pendingGaps = new ArrayList<>();
    private final long gapTimeoutMillis;

    public JdbcBroadcastSubscriber(
        Class<T> cls,
        QueueService queueService,
        JdbcQueueClient jdbcQueueClient,
        String queueName,
        MetricRegistry metricRegistry,
        IgnoreExecutionService ignoreExecutionService) {
        super(cls, queueService, jdbcQueueClient, queueName, metricRegistry, ignoreExecutionService);
        this.gapTimeoutMillis = jdbcQueueClient.getConfiguration().broadcastGapTimeout().toMillis();
    }

    @Override
    protected Integer poll(Consumer<byte[]> messageConsumer) {
        long now = System.currentTimeMillis();
        pruneExpiredGaps(now);
        List<JdbcQueueClient.OffsetRange> dueGaps = prepareDueGaps(now);

        Long previousMaxOffset = this.maxOffset != null ? this.maxOffset : 0L;
        JdbcQueueClient.BroadcastPollResult result = this.jdbcQueueClient.subscribeBroadcast(
            this.queueName,
            this.maxOffset,
            dueGaps,
            messageConsumer
        );

        handlePollResult(result, previousMaxOffset, now);

        return result.count();
    }

    @Override
    protected Integer pollBatch(Consumer<List<byte[]>> messageConsumer) {
        long now = System.currentTimeMillis();
        pruneExpiredGaps(now);
        List<JdbcQueueClient.OffsetRange> dueGaps = prepareDueGaps(now);

        Long previousMaxOffset = this.maxOffset != null ? this.maxOffset : 0L;
        JdbcQueueClient.BroadcastPollResult result = this.jdbcQueueClient.subscribeBroadcastBatch(
            this.queueName,
            this.maxOffset,
            dueGaps,
            messageConsumer
        );

        handlePollResult(result, previousMaxOffset, now);

        return result.count();
    }

    @Override
    protected void init() {
        maxOffset = this.jdbcQueueClient.fetchMaxOffset(this.queueName);
        this.pendingGaps.clear();
        this.markReady();
    }

    private void handlePollResult(JdbcQueueClient.BroadcastPollResult result, Long previousMaxOffset, long now) {
        if (result.deliveredOffsets().isEmpty()) {
            return;
        }

        long currentMaxOffset = previousMaxOffset;

        for (Long delivered : result.deliveredOffsets()) {
            if (delivered <= previousMaxOffset) {
                // Late-committing gap message delivered: split or remove matching gap range
                splitOrRemoveGap(delivered, now);
            } else {
                // New message delivered: if there's a gap between currentMaxOffset and delivered, track it
                if (delivered > currentMaxOffset + 1 && this.maxOffset != null) {
                    pendingGaps.add(new GapRange(currentMaxOffset + 1, delivered - 1, now, now, 0));
                }
                currentMaxOffset = delivered;
            }
        }

        if (currentMaxOffset > previousMaxOffset || this.maxOffset == null) {
            this.maxOffset = currentMaxOffset;
        }

        mergePendingGaps();
    }

    private void splitOrRemoveGap(long delivered, long now) {
        List<GapRange> replacements = new ArrayList<>();
        Iterator<GapRange> it = pendingGaps.iterator();
        while (it.hasNext()) {
            GapRange gap = it.next();
            if (gap.contains(delivered)) {
                it.remove();
                if (delivered > gap.fromOffset()) {
                    replacements.add(new GapRange(gap.fromOffset(), delivered - 1, gap.createdAt(), now, 0));
                }
                if (delivered < gap.toOffset()) {
                    replacements.add(new GapRange(delivered + 1, gap.toOffset(), gap.createdAt(), now, 0));
                }
            }
        }
        pendingGaps.addAll(replacements);
    }

    private List<JdbcQueueClient.OffsetRange> prepareDueGaps(long now) {
        if (pendingGaps.isEmpty()) {
            return Collections.emptyList();
        }

        List<JdbcQueueClient.OffsetRange> due = new ArrayList<>();
        for (GapRange gap : pendingGaps) {
            if (gap.isDue(now)) {
                due.add(new JdbcQueueClient.OffsetRange(gap.fromOffset(), gap.toOffset()));
                gap.markChecked(now);
                if (due.size() >= MAX_GAPS_PER_QUERY) {
                    break;
                }
            }
        }
        return due;
    }

    private void pruneExpiredGaps(long now) {
        if (pendingGaps.isEmpty()) {
            return;
        }

        Iterator<GapRange> it = pendingGaps.iterator();
        while (it.hasNext()) {
            GapRange gap = it.next();
            if (gap.isExpired(now, gapTimeoutMillis)) {
                if (log.isTraceEnabled()) {
                    log.trace(
                        "Broadcast gap range [{}, {}] timed out after {}ms on queue '{}'",
                        gap.fromOffset(), gap.toOffset(), now - gap.createdAt(), this.queueName
                    );
                }
                it.remove();
            }
        }
    }

    private void mergePendingGaps() {
        if (pendingGaps.size() <= 1) {
            return;
        }

        pendingGaps.sort(Comparator.comparingLong(GapRange::fromOffset));
        List<GapRange> merged = new ArrayList<>(pendingGaps.size());
        GapRange current = pendingGaps.get(0);

        for (int i = 1; i < pendingGaps.size(); i++) {
            GapRange next = pendingGaps.get(i);
            if (next.fromOffset() <= current.toOffset() + 1) {
                long newTo = Math.max(current.toOffset(), next.toOffset());
                long newCreatedAt = Math.min(current.createdAt(), next.createdAt());
                long newNextCheck = Math.min(current.nextCheckAt(), next.nextCheckAt());
                int newCheckCount = Math.min(current.checkCount(), next.checkCount());
                current = new GapRange(current.fromOffset(), newTo, newCreatedAt, newNextCheck, newCheckCount);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        pendingGaps.clear();
        pendingGaps.addAll(merged);

        if (pendingGaps.size() > MAX_PENDING_GAPS) {
            pendingGaps.subList(0, pendingGaps.size() - MAX_PENDING_GAPS).clear();
        }
    }

    List<GapRange> getPendingGaps() {
        return Collections.unmodifiableList(pendingGaps);
    }

    Long getMaxOffset() {
        return maxOffset;
    }

    static final class GapRange {
        private final long fromOffset;
        private final long toOffset;
        private final long createdAt;
        private long nextCheckAt;
        private int checkCount;

        GapRange(long fromOffset, long toOffset, long createdAt, long nextCheckAt, int checkCount) {
            this.fromOffset = fromOffset;
            this.toOffset = toOffset;
            this.createdAt = createdAt;
            this.nextCheckAt = nextCheckAt;
            this.checkCount = checkCount;
        }

        long fromOffset() {
            return fromOffset;
        }

        long toOffset() {
            return toOffset;
        }

        long createdAt() {
            return createdAt;
        }

        long nextCheckAt() {
            return nextCheckAt;
        }

        int checkCount() {
            return checkCount;
        }

        boolean isDue(long now) {
            return now >= nextCheckAt;
        }

        boolean isExpired(long now, long timeoutMillis) {
            return now - createdAt > timeoutMillis;
        }

        void markChecked(long now) {
            this.checkCount++;
            long delay = Math.min(50L * (1L << Math.min(checkCount, 5)), 2000L);
            this.nextCheckAt = now + delay;
        }

        boolean contains(long offset) {
            return offset >= fromOffset && offset <= toOffset;
        }
    }
}
