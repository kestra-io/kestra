package io.kestra.executor;

import java.time.Instant;
import java.util.function.Consumer;

import io.kestra.core.runners.ExecutionDelay;

/**
 * This state store is used by the {@link io.kestra.core.runners.Executor} to handle execution delays (Pause, retries, LoopUntil, ...).
 */
public interface ExecutionDelayStateStore {
    /**
     * Process expired execution delays using the provided consumer
     * This method is used periodically by the {@link io.kestra.core.runners.Executor} to process expired execution delays.
     *
     * @implNote Implementors must use some sort of transaction (FOR UPDATE SKIP LOCKED or {@link io.kestra.core.lock.LockService#tryLock(String, String, Runnable)}) for accuracy.
     */
    void processExpired(Instant now, Consumer<ExecutionDelay> consumer);

    /**
     * Save an execution delay.
     */
    void save(ExecutionDelay executionDelay);
}
