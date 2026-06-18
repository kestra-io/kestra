package io.kestra.worker;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.worker.WorkerGroups;
import io.micrometer.core.instrument.Counter;

/**
 * Derives how fast a single Worker completes tasks, reusing the existing
 * {@code worker.ended.count} counter — no extra counter is maintained and nothing
 * new is registered in Micrometer. The heartbeat thread calls
 * {@link #sampleRatePerSecond()} periodically; each call diffs the counter total
 * against the previous sample and feeds the instantaneous rate through an
 * exponential moving average so the value the UI shows doesn't jump between
 * samples. One instance per Worker.
 *
 * <p>Expected to be sampled from a single thread (the liveness heartbeat, which is
 * serialized under its state lock), so the book-keeping fields need no synchronization.
 */
public final class RateMeter {

    /** Smoothing factor: higher reacts faster, lower is steadier. */
    private static final double ALPHA = 0.3;

    private final MetricRegistry metricRegistry;
    private final String workerGroupId;

    private double lastCount;
    private long lastNanos;
    private double smoothedRate = 0.0;
    private boolean initialized = false;

    public RateMeter(final MetricRegistry metricRegistry, final String workerGroupId) {
        this.metricRegistry = metricRegistry;
        this.workerGroupId = workerGroupId;
    }

    /**
     * @return the EWMA-smoothed tasks/sec completed since the previous call. The first
     *         call only establishes the baseline and returns 0.
     */
    public double sampleRatePerSecond() {
        double current = currentTaskCount();
        long now = System.nanoTime();

        if (!initialized) {
            this.lastCount = current;
            this.lastNanos = now;
            this.initialized = true;
            return 0.0;
        }

        double elapsedSeconds = (now - lastNanos) / 1_000_000_000.0;
        double instantRate = elapsedSeconds > 0 ? (current - lastCount) / elapsedSeconds : 0.0;
        this.smoothedRate = ALPHA * instantRate + (1 - ALPHA) * smoothedRate;

        this.lastCount = current;
        this.lastNanos = now;
        return smoothedRate;
    }

    /**
     * Sums the existing {@code worker.ended.count} counter across all
     * task_type / namespace / flow series for this worker's group.
     */
    private double currentTaskCount() {
        return metricRegistry.find(MetricRegistry.METRIC_WORKER_ENDED_COUNT)
            .tag(MetricRegistry.TAG_WORKER_GROUP, WorkerGroups.normalize(workerGroupId))
            .counters()
            .stream()
            .mapToDouble(Counter::count)
            .sum();
    }
}
