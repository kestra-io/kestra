package io.kestra.core.runners;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Storage interface for concurrency slot monitors.
 * Monitors track when concurrency slots should be automatically released
 * if an execution holds a slot longer than the configured duration.
 */
public interface ConcurrencySlotMonitorStorage {
    /**
     * Save a concurrency slot monitor.
     *
     * @param monitor the monitor to save
     */
    void save(ConcurrencySlotMonitor monitor);

    /**
     * Delete the monitor for a specific execution.
     * Called when an execution terminates normally.
     *
     * @param executionId the execution ID
     */
    void delete(String executionId);

    /**
     * Process all monitors that have passed their deadline.
     * For each expired monitor, the consumer is called and then the monitor is deleted.
     *
     * @param now the current time to compare against deadlines
     * @param consumer callback for each expired monitor
     */
    void processExpired(Instant now, Consumer<ConcurrencySlotMonitor> consumer);
}
