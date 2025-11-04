package io.kestra.executor;

import io.kestra.core.runners.TransactionContext;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.core.runners.WorkerTask;

import java.util.function.BiConsumer;

/**
 * State store containing all workers' jobs in RUNNING state.
 *
 * @see WorkerJob
 */
public interface WorkerJobRunningStateStore {

    /**
     * Deletes a running worker job for the given key.
     *
     * <p>
     *     A key can be a {@link WorkerTask} Task Run ID.
     * </p>
     *
     * @param key the key of the worker job to be deleted.
     */
    void deleteByKey(String key);

    /**
     * Deletes a running worker job for the given key.
     *
     * <p>
     *     A key can be a {@link WorkerTask} Task Run ID.
     * </p>
     *
     * @param key the key of the worker job to be deleted.
     */
    void deleteByKey(TransactionContext txContext, String key);

    /**
     * Save a running worker job.
     * This is used by the {@link io.kestra.core.queues.WorkerJobQueueInterface} to save running worker jobs for resubmission.
     *
     * @implNote Implementors that support transaction must use the provided {@link TransactionContext} to attach to the current transaction.
     */
    WorkerJobRunning save(TransactionContext txContext, WorkerJobRunning workerJobRunning);

    /**
     * Process all running worker jobs for a dead worker.
     * This is used by the {@link}
     *
     * @implNote Implementors that support transaction must use the provided {@link TransactionContext} to attach to the current transaction.
     *           Implementors must use some sort of transaction (FOR UPDATE SKIP LOCKED or {@link io.kestra.core.lock.LockService#tryLock(String, String, Runnable)}) for accuracy.
     */
    void processWorkerJobsForDeadWorker(TransactionContext txContext, String workersUid, BiConsumer<TransactionContext, WorkerJobRunning> consumer);
}
