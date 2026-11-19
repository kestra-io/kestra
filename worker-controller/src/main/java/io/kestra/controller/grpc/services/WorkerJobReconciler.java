package io.kestra.controller.grpc.services;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import org.slf4j.event.Level;

import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.NoTransactionContext;
import io.kestra.core.runners.TransactionContext;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskRunning;
import io.kestra.core.runners.WorkerTriggerRunning;
import io.kestra.core.scheduler.events.TriggerWorkerLost;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.utils.Logs;
import io.kestra.core.worker.StickyWorkerRouting;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Compares the jobs a connected worker reports holding with the running entries recorded for it, and resubmits the
 * entries the worker cannot hold.
 * <p>
 * A job sent on a stream that stopped delivering never reaches the worker, yet its running entry stays recorded for a
 * worker that keeps heartbeating, so nothing else would ever resubmit it. Only the entries recorded on an earlier
 * stream are considered: the worker no longer receives anything from such a stream, so an entry it does not report is
 * lost. An entry recorded on the current stream may still be on its way; if it was lost, the stream is broken and the
 * worker reconnects, after which the entry belongs to an earlier stream.
 */
@Singleton
@Slf4j
public class WorkerJobReconciler {

    private final WorkerJobRunningStateStore workerJobRunningStateStore;
    private final KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue;
    private final TriggerEventQueue triggerEventQueue;
    private final StickyWorkerRouting stickyWorkerRouting;
    private final ExecutionRepositoryInterface executionRepository;
    private final KillSwitchService killSwitchService;
    private final MetricRegistry metricRegistry;

    @Inject
    public WorkerJobReconciler(WorkerJobRunningStateStore workerJobRunningStateStore,
        KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue,
        TriggerEventQueue triggerEventQueue,
        StickyWorkerRouting stickyWorkerRouting,
        ExecutionRepositoryInterface executionRepository,
        KillSwitchService killSwitchService,
        MetricRegistry metricRegistry) {
        this.workerJobRunningStateStore = Objects.requireNonNull(workerJobRunningStateStore, "workerJobRunningStateStore must not be null");
        this.workerJobEventQueue = Objects.requireNonNull(workerJobEventQueue, "workerJobEventQueue must not be null");
        this.triggerEventQueue = Objects.requireNonNull(triggerEventQueue, "triggerEventQueue must not be null");
        this.stickyWorkerRouting = Objects.requireNonNull(stickyWorkerRouting, "stickyWorkerRouting must not be null");
        this.executionRepository = Objects.requireNonNull(executionRepository, "executionRepository must not be null");
        this.killSwitchService = Objects.requireNonNull(killSwitchService, "killSwitchService must not be null");
        this.metricRegistry = Objects.requireNonNull(metricRegistry, "metricRegistry must not be null");
    }

    /**
     * Resubmits the jobs recorded for the worker on a stream older than {@code streamGeneration} that are not in
     * {@code heldJobIds}.
     *
     * @param workerId the worker that sent the sync
     * @param workerGroupId the worker group of that worker
     * @param streamGeneration the generation of the stream the sync was sent on; {@code 0} disables reconciliation
     * @param heldJobIds the jobs the worker holds: received, and whose terminal result the controller has not accepted
     */
    public void reconcile(String workerId, String workerGroupId, long streamGeneration, Collection<String> heldJobIds) {
        if (streamGeneration <= 0) {
            return;
        }

        Set<String> held = Set.copyOf(heldJobIds);
        workerJobRunningStateStore.processWorkerJobsForDeadWorker(NoTransactionContext.INSTANCE, workerId, (txContext, workerJobRunning) ->
        {
            if (held.contains(workerJobRunning.uid()) || workerJobRunning.getWorkerInstance().streamGeneration() >= streamGeneration) {
                return;
            }
            if (resubmit(txContext, workerJobRunning, workerId)) {
                metricRegistry.counter(
                    MetricRegistry.METRIC_CONTROLLER_JOB_RESUBMITTED_TOTAL,
                    MetricRegistry.METRIC_CONTROLLER_JOB_RESUBMITTED_TOTAL_DESCRIPTION,
                    metricRegistry.workerGroupAndQueueTags(workerGroupId, workerJobRunning.getWorkerInstance().workerQueueId())
                ).increment();
            }
        });
    }

    private boolean resubmit(TransactionContext txContext, WorkerJobRunning workerJobRunning, String workerId) {
        log.warn(
            "Resubmitting the job '{}': it was sent to worker '{}' on job stream {}, which the worker has left without receiving it.",
            workerJobRunning.uid(), workerId, workerJobRunning.getWorkerInstance().streamGeneration()
        );
        return switch (workerJobRunning) {
            case WorkerTaskRunning workerTaskRunning -> resubmitTask(txContext, workerTaskRunning, workerId);
            case WorkerTriggerRunning workerTriggerRunning -> releaseTrigger(txContext, workerTriggerRunning, workerId);
            default -> false;
        };
    }

    private boolean resubmitTask(TransactionContext txContext, WorkerTaskRunning workerTaskRunning, String workerId) {
        if (mustNotRun(workerTaskRunning.getTaskRun())) {
            log.warn(
                "Discarding the running entry of task run '{}' of execution '{}' instead of resubmitting it, because the task run has already ended, its execution was stopped or no longer exists, or a kill switch applies to it.",
                workerTaskRunning.getTaskRun().getId(),
                workerTaskRunning.getTaskRun().getExecutionId()
            );
            workerJobRunningStateStore.deleteByKeyAndWorker(txContext, workerTaskRunning.uid(), workerId);
            return false;
        }

        String workerQueueId = workerTaskRunning.getWorkerInstance().workerQueueId();
        WorkerTask workerTask = WorkerTask.builder()
            .taskRun(workerTaskRunning.getTaskRun())
            .task(workerTaskRunning.getTask())
            .data(workerTaskRunning.getData())
            .executionKind(workerTaskRunning.getExecutionKind())
            .workingDirectoryId(workerTaskRunning.getWorkingDirectoryId())
            .build();

        // A task of a WorkingDirectory needs the folder its worker holds, so it goes back to that same worker.
        WorkerJobEvent event = workerTask.getWorkingDirectoryId() == null
            ? WorkerJobEvent.of(workerTask, workerQueueId)
            : stickyWorkerRouting.route(workerTask, workerQueueId, workerId);
        try {
            workerJobEventQueue.emit(event.key(), event);
            // The entry is released only once the job is back on the queue, otherwise the next sync would resubmit it again.
            workerJobRunningStateStore.deleteByKeyAndWorker(txContext, workerTaskRunning.uid(), workerId);
            Logs.logTaskRun(workerTaskRunning.getTaskRun(), Level.WARN, "Resubmit WorkerTask: it never reached its worker.");
            return true;
        } catch (QueueException e) {
            Logs.logTaskRun(workerTaskRunning.getTaskRun(), Level.ERROR, "Unable to resubmit WorkerTask.", e);
            return false;
        }
    }

    /**
     * Applies the checks of the orphan sweep: a task run is not resubmitted once a kill switch applies to it, its
     * execution no longer exists or was stopped, or it already ended. An execution that cannot be read is not taken as
     * proof that the task run ended, so the task is resubmitted rather than risking to lose it.
     */
    private boolean mustNotRun(TaskRun taskRun) {
        if (killSwitchService.evaluate(taskRun) != EvaluationType.PASS) {
            return true;
        }
        try {
            return executionRepository.findByIdWithoutAcl(taskRun.getTenantId(), taskRun.getExecutionId())
                .map(execution -> execution.hasTaskRunEnded(taskRun.getId()))
                .orElse(true);
        } catch (RuntimeException e) {
            log.warn("Unable to read execution '{}' to check whether task run '{}' has already ended.", taskRun.getExecutionId(), taskRun.getId(), e);
            return false;
        }
    }

    /**
     * Hands the trigger back to the scheduler, which resubmits it from the current flow definition rather than from the
     * recorded one, the same way it does for a trigger whose worker is gone.
     */
    private boolean releaseTrigger(TransactionContext txContext, WorkerTriggerRunning workerTriggerRunning, String workerId) {
        TriggerId triggerId = workerTriggerRunning.triggerId();
        workerJobRunningStateStore.deleteByKeyAndWorker(txContext, workerTriggerRunning.uid(), workerId);
        triggerEventQueue.send(new TriggerWorkerLost(triggerId, workerId, workerTriggerRunning.getDispatchEpoch()));
        Logs.logTrigger(triggerId, Level.WARN, "Trigger never reached worker '{}', notifying the scheduler.", workerId);
        return true;
    }
}
