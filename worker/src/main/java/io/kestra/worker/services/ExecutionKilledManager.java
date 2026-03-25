package io.kestra.worker.services;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.event.Level;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.ExecutionKilledTrigger;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTrigger;
import io.kestra.core.utils.Logs;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages execution-killed state and kill callbacks for the worker.
 * <p>
 * This class:
 * <ul>
 * <li>Maintains a cache of killed execution IDs for pre-processing checks</li>
 * <li>Tracks running jobs with their kill callbacks</li>
 * <li>Matches incoming kill events against running jobs and invokes callbacks</li>
 * </ul>
 */
@Singleton
@Slf4j
public class ExecutionKilledManager {

    private static final Duration KILLED_CACHE_TTL = Duration.ofHours(24);

    private final MetricRegistry metricRegistry;

    /**
     * Cache of killed execution IDs with TTL auto-eviction.
     */
    private final Cache<String, ExecutionKilledExecution> killedExecutions;

    /**
     * Registry of running jobs with their kill callbacks.
     */
    private final ConcurrentHashMap<String, KillableJob> runningJobs = new ConcurrentHashMap<>();

    @Inject
    public ExecutionKilledManager(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
        this.killedExecutions = Caffeine.newBuilder()
            .expireAfterWrite(KILLED_CACHE_TTL)
            .build();
    }

    /**
     * Called when a kill command is received via the gRPC stream.
     *
     * @param killed the kill event
     */
    public void onKillReceived(ExecutionKilled killed) {
        if (killed instanceof ExecutionKilledExecution killedExecution) {
            log.info("[tenant: {}] [execution: {}] Received kill command", killedExecution.getTenantId(), killedExecution.getExecutionId());
            killedExecutions.put(killedExecution.getExecutionId(), killedExecution);

            metricRegistry
                .counter(MetricRegistry.METRIC_WORKER_KILLED_COUNT, MetricRegistry.METRIC_WORKER_KILLED_COUNT_DESCRIPTION)
                .increment();

            // Kill any matching running jobs
            runningJobs.forEach((_, killableJob) ->
            {
                if (killableJob.job() instanceof WorkerTask workerTask && killedExecution.isEqual(workerTask)) {
                    Logs.logTaskRun(workerTask.getTaskRun(), Level.INFO, "Killing running task");
                    killableJob.killAction().run();
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
                    killableJob.killAction().run();
                }
            });
        }
    }

    /**
     * Registers a running job with its kill callback.
     *
     * @param jobUid the unique identifier of the job
     * @param job the worker job
     * @param killAction the action to invoke to kill the job
     */
    public void register(String jobUid, WorkerJob job, Runnable killAction) {
        runningJobs.put(jobUid, new KillableJob(job, killAction));
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

    record KillableJob(WorkerJob job, Runnable killAction) {
    }
}
