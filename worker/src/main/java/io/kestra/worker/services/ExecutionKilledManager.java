package io.kestra.worker.services;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.event.Level;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.ExecutionKilledTaskRuns;
import io.kestra.core.models.executions.ExecutionKilledTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.utils.Logs;

import io.micrometer.core.instrument.Counter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages execution-killed state and kill callbacks for the worker.
 * <p>
 * This class:
 * <ul>
 * <li>Maintains a cache of killed execution IDs for pre-processing checks</li>
 * <li>Tracks running jobs with their interrupt callbacks</li>
 * <li>Matches incoming kill/cancel events against running jobs and invokes callbacks, remembering
 * task-run-scoped cancellations that arrive before the matching job is registered</li>
 * </ul>
 */
@Singleton
@Slf4j
public class ExecutionKilledManager {

    private static final Duration KILLED_CACHE_TTL = Duration.ofHours(24);

    /**
     * Cache of killed execution IDs with TTL auto-eviction.
     */
    private final Cache<String, ExecutionKilledExecution> killedExecutions;

    /**
     * Task-run-scoped interrupts received before the matching job was registered on this worker,
     * applied as soon as {@link #register} sees a matching job.
     */
    private final Cache<String, State.Type> pendingTaskRunInterrupts;

    /**
     * Registry of running jobs with their interrupt callbacks.
     */
    private final ConcurrentHashMap<String, KillableJob> runningJobs = new ConcurrentHashMap<>();

    private final Counter workerKilledCounter;

    @Inject
    public ExecutionKilledManager(MetricRegistry metricRegistry) {
        this.killedExecutions = Caffeine.newBuilder()
            .expireAfterWrite(KILLED_CACHE_TTL)
            .build();
        this.pendingTaskRunInterrupts = Caffeine.newBuilder()
            .expireAfterWrite(KILLED_CACHE_TTL)
            .build();
        this.workerKilledCounter = metricRegistry.counter(
            MetricRegistry.METRIC_WORKER_KILLED_COUNT,
            MetricRegistry.METRIC_WORKER_KILLED_COUNT_DESCRIPTION
        );
    }

    /**
     * Called when a kill command is received via the gRPC stream.
     *
     * @param killed the kill event
     */
    public void onKillReceived(ExecutionKilled killed) {
        if (killed instanceof ExecutionKilledExecution killedExecution) {
            log.info("[tenant: {}] [execution: {}] Received kill command", killedExecution.getTenantId(), killedExecution.getExecutionId());
            if (killedExecution.getTaskRunId() == null) {
                killedExecutions.put(killedExecution.getExecutionId(), killedExecution);
            }

            workerKilledCounter.increment();

            // Kill any matching running jobs
            runningJobs.forEach((_, killableJob) ->
            {
                if (killableJob.job() instanceof WorkerTask workerTask && killedExecution.isEqual(workerTask)) {
                    Logs.logTaskRun(workerTask.getTaskRun(), Level.INFO, "Killing running task");
                    State.Type killState = killedExecution.getExecutionState() != null ? killedExecution.getExecutionState() : State.Type.KILLED;
                    killableJob.interruptAction().accept(killState);
                }
            });
        } else if (killed instanceof ExecutionKilledTrigger killedTrigger) {
            log.info(
                "[tenant: {}] [namespace: {}] [flow: {}] [trigger: {}] Received kill command",
                killedTrigger.getTenantId(), killedTrigger.getNamespace(), killedTrigger.getFlowId(), killedTrigger.getTriggerId()
            );

            // Kill any matching running trigger jobs
            runningJobs.forEach((_, killableJob) ->
            {
                if (killableJob.job() instanceof WorkerTrigger workerTrigger && killedTrigger.isEqual(workerTrigger.triggerId())) {
                    Logs.logTrigger(workerTrigger.triggerId(), Level.INFO, "Killing running trigger");
                    killableJob.interruptAction().accept(State.Type.KILLED);
                }
            });
        } else if (killed instanceof ExecutionKilledTaskRuns killedTaskRuns) {
            log.info("[tenant: {}] [execution: {}] Received task run interrupt command for {}", killedTaskRuns.getTenantId(), killedTaskRuns.getExecutionId(), killedTaskRuns.getTaskRunIds());

            // Store all pending task run interrupts in the cache so, if they arrive later, they will still be interrupted.
            killedTaskRuns.getTaskRunIds().forEach(taskRunId -> pendingTaskRunInterrupts.put(taskRunId, killedTaskRuns.getTaskRunState()));

            runningJobs.forEach((_, killableJob) ->
            {
                if (killableJob.job() instanceof WorkerTask workerTask && killedTaskRuns.isFor(workerTask)) {
                    State.Type pendingState = pendingTaskRunInterrupts.asMap().remove(workerTask.getTaskRun().getId());
                    if (pendingState != null) {
                        Logs.logTaskRun(workerTask.getTaskRun(), Level.INFO, "Cancelling running task");
                        killableJob.interruptAction().accept(pendingState);
                    }
                }
            });
        }
    }

    /**
     * Registers a running job with its interrupt callback. If a task-run-scoped interrupt for this
     * job was received before it got here, it is applied immediately and the pending entry removed.
     *
     * @param jobUid the unique identifier of the job
     * @param job the worker job
     * @param interruptAction the action to invoke to interrupt the job, given the state to report
     */
    public void register(String jobUid, WorkerJob job, Consumer<State.Type> interruptAction) {
        runningJobs.put(jobUid, new KillableJob(job, interruptAction));

        if (job instanceof WorkerTask workerTask && workerTask.getTaskRun().getId() != null) {
            State.Type pendingState = pendingTaskRunInterrupts.asMap().remove(workerTask.getTaskRun().getId());
            if (pendingState != null) {
                Logs.logTaskRun(workerTask.getTaskRun(), Level.INFO, "Cancelling running task");
                interruptAction.accept(pendingState);
            }
        }
    }

    /**
     * Unregisters a job when it completes.
     *
     * @param jobUid the unique identifier of the job
     */
    public void unregister(String jobUid) {
        runningJobs.remove(jobUid);
    }

    /**
     * Checks if an execution has been killed.
     *
     * @param executionId the execution ID to check
     * @return true if the execution has been killed
     */
    public boolean isExecutionKilled(String executionId) {
        return killedExecutions.getIfPresent(executionId) != null;
    }

    record KillableJob(WorkerJob job, Consumer<State.Type> interruptAction) {
    }
}
