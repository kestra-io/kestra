package io.kestra.core.services;

import java.util.List;
import java.util.Optional;

import io.kestra.core.exceptions.NoMatchingWorkerQueueException;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.tasks.SystemTask;
import io.kestra.core.models.tasks.WorkerSelector;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerQueueRouting;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.worker.WorkerQueues;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Service that resolves the target Worker Queue for a Worker Job.
 */
public interface WorkerQueueService {

    /**
     * Resolves the target Worker Queue routing for a Worker Job.
     *
     * @param flow      the flow that owns the job
     * @param workerJob the worker job (task or trigger) being routed
     * @return the resolved {@link WorkerQueueRouting}, or {@link Optional#empty()} when
     *         no routing is configured (the job will run on the global default queue)
     * @throws NoMatchingWorkerQueueException when {@code workerSelector.tags} declare a routing
     *         requirement that cannot be satisfied by any existing Worker Queue and the
     *         configured fallback is FAIL, WAIT, or CANCEL — a missing queue is a
     *         configuration error.
     */
    Optional<WorkerQueueRouting> resolveWorkerQueueForJob(FlowInterface flow, WorkerJob workerJob) throws NoMatchingWorkerQueueException;

    /**
     * Default implementation of {@link WorkerQueueService}.
     *
     * <p>Enforces the {@link SystemTask} reservation: any {@code WorkerTask} whose task is
     * a {@link SystemTask} is routed to the reserved {@link WorkerQueues#SYSTEM_ID} queue
     * served by the in-process SystemWorker, bypassing any other resolution rule. A
     * {@code workerSelector.tags} declared on a SystemTask is silently ignored apart from
     * a warning log entry.
     *
     * <p>All other jobs delegate to {@link #doResolveWorkerQueueForJob(FlowInterface, WorkerJob)},
     * which returns no routing by default. Subclasses may override this hook to add
     * tag-based resolution.
     */
    @Slf4j
    @Singleton
    @Requires(missingBeans = WorkerQueueService.class)
    @Secondary
    class Default implements WorkerQueueService {

        @Override
        public final Optional<WorkerQueueRouting> resolveWorkerQueueForJob(FlowInterface flow, WorkerJob workerJob) throws NoMatchingWorkerQueueException {
            if (workerJob instanceof WorkerTask workerTask && workerTask.getTask() instanceof SystemTask) {
                WorkerSelector selector = workerTask.getTask().getWorkerSelector();
                List<String> tags = selector != null ? selector.tags() : null;
                if (tags != null && !tags.isEmpty()) {
                    log.warn(
                        "Task {} is a SystemTask; ignoring workerSelector.tags={} and routing to '{}'",
                        workerTask.getTask().getType(), tags, WorkerQueues.SYSTEM_ID
                    );
                }
                return Optional.of(WorkerQueueRouting.forSystem());
            }
            return doResolveWorkerQueueForJob(flow, workerJob);
        }

        /**
         * Resolves the target Worker Queue routing for a non-{@link SystemTask} Worker Job.
         * Returns {@link Optional#empty()} by default (no routing → default queue).
         * Subclasses may override this hook to add tag-based resolution.
         *
         * <p>Subclasses must not handle the SystemTask reservation — it is enforced by
         * {@link #resolveWorkerQueueForJob(FlowInterface, WorkerJob)} before delegating here.
         */
        protected Optional<WorkerQueueRouting> doResolveWorkerQueueForJob(FlowInterface flow, WorkerJob workerJob) throws NoMatchingWorkerQueueException {
            return Optional.empty();
        }
    }
}
