package io.kestra.executor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.slf4j.event.Level;

import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.killswitch.EvaluationType;
import io.kestra.core.killswitch.KillSwitchService;
import io.kestra.core.lock.LockService;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.runners.*;
import io.kestra.core.scheduler.events.TriggerWorkerLost;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.scheduler.vnodes.VNodeController;
import io.kestra.core.server.*;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.Logs;

import io.micrometer.core.instrument.Counter;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.server.Service.ServiceState.*;

/**
 * Responsible for coordinating the state of all service instances.
 *
 * @see ServiceInstance
 */
@Slf4j
@Context
@Requires(property = "kestra.server-type", pattern = "(EXECUTOR|STANDALONE)")
public class DefaultServiceLivenessCoordinator extends AbstractServiceLivenessTask {

    private static final int DEFAULT_SCHEDULE_JITTER_MAX_MS = 500;

    private static final String DEFAULT_REASON_FOR_DISCONNECTED = "The service was detected as non-responsive after the session timeout. " +
        "Service transitioned to the 'DISCONNECTED' state.";

    private static final String DEFAULT_REASON_FOR_NOT_RUNNING = "The service was detected as non-responsive or terminated after termination grace period. " +
        "Service transitioned to the 'NOT_RUNNING' state.";

    private static final String TASK_NAME = "service-liveness-coordinator-task";

    private final ServiceLivenessStore store;
    private final ServiceRegistry serviceRegistry;
    private final ServiceLivenessUpdater serviceLivenessUpdater;
    private final ServiceInstanceRepositoryInterface serviceInstanceRepository;
    private final Duration purgeRetention;

    private final LockService lockService;
    private final KillSwitchService killSwitchService;
    private final KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue;
    private final WorkerJobRunningStateStore workerJobRunningStateStore;
    private final TriggerEventQueue triggerEventQueue;
    private final MetricRegistry metricRegistry;
    private final VNodeController vNodeController;
    private Counter workerJobResubmitCounter;
    // mutable for testing purpose
    String serverId = ServerInstance.INSTANCE_ID;

    @VisibleForTesting
    void setServerInstance(String serverId) {
        this.serverId = serverId;
    }

    /**
     * Creates a new {@link DefaultServiceLivenessCoordinator} instance.
     *
     * @param store The {@link ServiceInstanceRepositoryInterface}.
     * @param serverConfig The server configuration.
     */
    @Inject
    public DefaultServiceLivenessCoordinator(final ServiceLivenessStore store,
        final ServiceRegistry serviceRegistry,
        final ServiceLivenessUpdater serviceLivenessUpdater,
        final ServiceInstanceRepositoryInterface serviceInstanceRepository,
        final LockService lockService,
        final KillSwitchService killSwitchService,
        final KeyedDispatchQueueInterface<WorkerJobEvent> workerJobEventQueue,
        final WorkerJobRunningStateStore workerJobRunningStateStore,
        final TriggerEventQueue triggerEventQueue,
        final ServerConfig serverConfig,
        final MetricRegistry metricRegistry,
        final VNodeController vNodeController) {
        super(TASK_NAME, serverConfig);
        this.serviceRegistry = serviceRegistry;
        this.serviceLivenessUpdater = serviceLivenessUpdater;
        this.serviceInstanceRepository = serviceInstanceRepository;
        this.store = store;
        this.killSwitchService = killSwitchService;
        this.workerJobEventQueue = workerJobEventQueue;
        this.workerJobRunningStateStore = workerJobRunningStateStore;
        this.triggerEventQueue = triggerEventQueue;
        this.lockService = lockService;
        this.metricRegistry = metricRegistry;
        this.purgeRetention = serverConfig.service() != null && serverConfig.service().purge() != null
            ? serverConfig.service().purge().retention()
            : null;
        this.vNodeController = vNodeController;
    }

    @PostConstruct
    void initMetrics() {
        this.workerJobResubmitCounter = metricRegistry.counter(
            MetricRegistry.METRIC_EXECUTOR_WORKER_JOB_RESUBMIT_COUNT,
            MetricRegistry.METRIC_EXECUTOR_WORKER_JOB_RESUBMIT_COUNT_DESCRIPTION
        );
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected void onSchedule(Instant now) throws Exception {
        if (
            Optional.ofNullable(serviceRegistry.get(ServiceType.EXECUTOR))
                .filter(service -> service.instance().is(RUNNING))
                .isEmpty()
        ) {
            log.debug(
                "The liveness coordinator task was temporarily disabled. Executor is not yet in the RUNNING state."
            );
            return;
        }

        // Update all RUNNING but non-responding services to DISCONNECTED.
        handleAllNonRespondingServices(now);

        // Handle all workers which are not in a RUNNING state.
        handleAllWorkersForUncleanShutdown(now);

        // Update all services in one of the TERMINATED states to NOT_RUNNING.
        handleAllServicesForTerminatedStates(now);

        // Update all services in NOT_RUNNING to EMPTY (a.k.a soft delete).
        handleAllServiceInNotRunningState();

        maybeDetectAndLogNewConnectedServices();

        // May reassign scheduler VNodes
        vNodeController.checkServicesAndRebalanceVNodes();
    }

    /**
     * Handles all worker services which are shutdown or considered to be terminated.
     * <p>
     * This method may re-submit tasks is necessary.
     *
     * @param now the time of the execution.
     */
    protected void handleAllWorkersForUncleanShutdown(Instant now) {
        serviceInstanceRepository.processAllNonRunningInstances((txContext, serviceInstance) ->
        {
            if (!serviceInstance.is(ServiceType.WORKER)) {
                return;
            }

            // List of workers for which we don't know the actual state of tasks executions.
            // Re-emit all WorkerJobs for unclean workers
            boolean isUncleanShutdownService = isUncleanShutdownService(serviceInstance, now);
            if (isUncleanShutdownService) {
                if (serviceInstance.config().workerTaskRestartStrategy().isRestartable()) {
                    log.info("Trigger task restart for non-responding worker after termination grace period: {}.", serviceInstance.uid());
                    reEmitWorkerJobsForWorker(txContext, serviceInstance.uid());
                }
            } else if (serviceInstance.is(Service.ServiceState.TERMINATED_GRACEFULLY)) {
                // triggers whose terminal result was not flushed need to be released even when terminated gracefully
                if (serviceInstance.config().workerTaskRestartStrategy().isRestartable()) {
                    log.info("Trigger restart for terminated gracefully worker: {}.", serviceInstance.uid());
                    notifyTriggersLostForWorker(txContext, serviceInstance.uid());
                }
            }

            // Transit GRACEFUL or UNCLEAN SHUTDOWN worker to NOT_RUNNING.
            if (isUncleanShutdownService || serviceInstance.is(Service.ServiceState.TERMINATED_GRACEFULLY)) {
                serviceInstanceRepository.mayTransitServiceTo(
                    txContext,
                    serviceInstance,
                    Service.ServiceState.NOT_RUNNING,
                    DEFAULT_REASON_FOR_NOT_RUNNING
                );
            }
        });
    }

    /**
     * {@inheritDoc}
     **/
    protected void update(ServiceInstance instance, Service.ServiceState state, String reason) {
        serviceLivenessUpdater.update(instance, state, reason);
    }

    /**
     * Handles all unresponsive services and update their status to disconnected.
     * <p>
     * This method may re-submit tasks is necessary.
     *
     * @param now the time of the execution.
     */
    protected void handleAllNonRespondingServices(Instant now) {
        // Retrieves all services that are supposed to be running.
        serviceInstanceRepository.processInstanceInStates(allRunningStates(), (txContext, serviceInstance) ->
        {
            // Detect and handle non-responding services.
            if (isNonRespondingService(serviceInstance, now)) {
                // Attempt to transit all non-responding services to DISCONNECTED.
                serviceInstanceRepository.mayTransitServiceTo(
                    txContext,
                    serviceInstance,
                    Service.ServiceState.DISCONNECTED,
                    DEFAULT_REASON_FOR_DISCONNECTED
                );

                // Eventually restart worker tasks
                if (
                    serviceInstance.is(ServiceType.WORKER) &&
                        serviceInstance.config().workerTaskRestartStrategy().equals(WorkerTaskRestartStrategy.IMMEDIATELY)
                ) {
                    log.info("Trigger task restart for non-responding worker after timeout: {}.", serviceInstance.uid());
                    reEmitWorkerJobsForWorker(txContext, serviceInstance.uid());
                }
                mayReleaseLocksForService(serviceInstance, "service disconnected");
            }
        });
    }

    private void mayReleaseLocksForService(ServiceInstance serviceInstance, String reason) {
        // Eventually release all owned locks
        lockService.releaseAllLocks(serviceInstance.server().id())
            .forEach(l -> log.info("Released lock '{}' for service instance '{}'. Reason: {}", IdUtils.fromParts(l.getCategory(), l.getId()), serviceInstance.uid(), reason));
    }

    @Scheduled(initialDelay = "${kestra.server.service.purge.initial-delay}", fixedDelay = "${kestra.server.service.purge.fixed-delay}")
    public void purgeEmptyInstances() {
        int purged = serviceInstanceRepository.purgeEmptyInstances(Instant.now().minus(purgeRetention));
        log.info("Purged {} service instances", purged);
    }

    private void reEmitWorkerJobsForWorker(final TransactionContext txContext, final String id) {
        workerJobResubmitCounter.increment();

        workerJobRunningStateStore.processWorkerJobsForDeadWorker(txContext, id, (txContext2, workerJobRunning) ->
        {
            resubmitWorkerJobRunning(txContext2, workerJobRunning);
        });
    }

    private void notifyTriggersLostForWorker(final TransactionContext txContext, final String id) {
        workerJobRunningStateStore.processWorkerJobsForDeadWorker(txContext, id, (txContext2, workerJobRunning) ->
        {
            if (workerJobRunning instanceof WorkerTriggerRunning workerTriggerRunning) {
                notifyTriggerWorkerLost(txContext2, workerTriggerRunning);
            }
        });
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected Duration getScheduleInterval() {
        // Multiple Executors can be running in parallel. We add a jitter to
        // help distributing the load more evenly among the ServiceLivenessCoordinator.
        // This is also used to prevent all ServiceLivenessCoordinator from attempting to query the repository simultaneously.
        Random r = new Random(); //SONAR
        int jitter = r.nextInt(DEFAULT_SCHEDULE_JITTER_MAX_MS);
        return serverConfig.liveness().interval().plus(Duration.ofMillis(jitter));
    }

    private boolean isUncleanShutdownService(final ServiceInstance instance, final Instant now) {
        // ...all services that have transitioned to DISCONNECTED or TERMINATING for more than terminationGracePeriod.
        if (instance.state().isDisconnectedOrTerminating() && instance.isTerminationGracePeriodElapsed(now)) {
            maybeLogNonRespondingAfterTerminationGracePeriod(instance, now);
            return true;
        }

        // ...all services that have transitioned to TERMINATED_FORCED.
        // Only select workers that have been terminated for at least the grace period, to ensure that all in-flight
        // task runs had enough time to be fully handled by the executors.
        return instance.is(Service.ServiceState.TERMINATED_FORCED) && instance.isTerminationGracePeriodElapsed(now);
    }

    private boolean isNonRespondingService(final ServiceInstance instance, final Instant now) {
        boolean isNonResponding = instance.config() != null && // protect against non-complete instance
            instance.config().liveness().enabled() &&
            instance.isSessionTimeoutElapsed(now) &&
            // exclude any service running on the same server as the executor, to prevent the latter from shutting down.
            !instance.server().id().equals(serverId) &&
            // only keep services eligible for liveness probe
            instance.createdAt().isBefore(now.minus(instance.config().liveness().initialDelay()));

        // warn
        if (isNonResponding) {
            log.warn(
                "Detected non-responding service [id={}, type={}, hostname={}] after timeout ({}ms).",
                instance.uid(),
                instance.type(),
                instance.server().hostname(),
                now.toEpochMilli() - instance.updatedAt().toEpochMilli()
            );
        }

        return isNonResponding;

    }

    private void handleAllServiceInNotRunningState() {
        // Soft delete all services which are NOT_RUNNING anymore.
        store.findAllInstancesInStates(Set.of(Service.ServiceState.NOT_RUNNING))
            .forEach(instance ->
            {
                safelyUpdate(instance, Service.ServiceState.INACTIVE, null);
                mayReleaseLocksForService(instance, "service inactive");
            });
    }

    private void handleAllServicesForTerminatedStates(final Instant now) {
        store
            .findAllInstancesInStates(Set.of(DISCONNECTED, TERMINATING, TERMINATED_GRACEFULLY, TERMINATED_FORCED))
            .stream()
            .filter(instance -> !instance.is(ServiceType.WORKER)) // WORKERS are handle above.
            .filter(instance -> instance.isTerminationGracePeriodElapsed(now) || instance.state().equals(TERMINATED_GRACEFULLY))
            .peek(instance -> maybeLogNonRespondingAfterTerminationGracePeriod(instance, now))
            .forEach(instance -> safelyUpdate(instance, NOT_RUNNING, DEFAULT_REASON_FOR_NOT_RUNNING));
    }

    private void maybeDetectAndLogNewConnectedServices() {
        if (log.isDebugEnabled()) {
            // Log the newly-connected services (useful for troubleshooting).
            store.findAllInstancesInStates(Set.of(CREATED, RUNNING))
                .stream()
                .filter(instance -> instance.createdAt().isAfter(lastScheduledExecution()))
                .forEach(instance ->
                {
                    log.debug(
                        "Detected new service [id={}, type={}, hostname={}] (started at: {}).",
                        instance.uid(),
                        instance.type(),
                        instance.server().hostname(),
                        instance.createdAt()
                    );
                });
        }
    }

    private void safelyUpdate(final ServiceInstance instance,
        final Service.ServiceState state,
        final String reason) {
        try {
            update(instance, state, reason);
        } catch (Exception e) {
            // Log and ignore exception - it's safe to ignore error because the run() method is supposed to schedule at fix rate.
            log.error(
                "Unexpected error while service [id={}, type={}, hostname={}] transition from {} to {}.",
                instance.uid(),
                instance.type(),
                instance.server().hostname(),
                instance.state(),
                state,
                e
            );
        }
    }

    private void maybeLogNonRespondingAfterTerminationGracePeriod(final ServiceInstance instance,
        final Instant now) {
        if (instance.state().isDisconnectedOrTerminating()) {
            log.warn(
                "Detected non-responding service [id={}, type={}, hostname={}] after termination grace period ({}ms).",
                instance.uid(),
                instance.type(),
                instance.server().hostname(),
                now.toEpochMilli() - instance.updatedAt().toEpochMilli()
            );
        }
    }

    private void resubmitWorkerJobRunning(TransactionContext txContext, WorkerJobRunning workerJobRunning) {
        // WorkerTaskRunning
        if (workerJobRunning instanceof WorkerTaskRunning workerTaskRunning) {
            resubmitWorkerTask(txContext, workerTaskRunning);
        }

        // WorkerTriggerRunning
        if (workerJobRunning instanceof WorkerTriggerRunning workerTriggerRunning) {
            notifyTriggerWorkerLost(txContext, workerTriggerRunning);
        }
    }

    private void resubmitWorkerTask(TransactionContext txContext, WorkerTaskRunning workerTaskRunning) {
        if (killSwitchService.evaluate(workerTaskRunning.getTaskRun()) != EvaluationType.PASS) {
            // if the execution is switch-killed, we remove the workerTaskRunning and skip its resubmission
            log.warn("Ignoring worker job resubmission for execution {} because there is a kill switch for it", workerTaskRunning.getTaskRun().getExecutionId());
            workerJobRunningStateStore.deleteByKey(txContext, workerTaskRunning.uid());
        } else {
            try {
                String raw = workerTaskRunning.getWorkerInstance().workerQueueId();
                String workerQueueId = (raw == null || raw.isEmpty()) ? null : raw;
                WorkerTask workerTask = WorkerTask.builder()
                    .taskRun(workerTaskRunning.getTaskRun().onRunningResend())
                    .task(workerTaskRunning.getTask())
                    .data(workerTaskRunning.getData())
                    .build();
                workerJobEventQueue.emit(workerQueueId, WorkerJobEvent.of(workerTask, workerQueueId));
                Logs.logTaskRun(
                    workerTaskRunning.getTaskRun(),
                    Level.WARN,
                    "Resubmit WorkerTask."
                );
            } catch (QueueException e) {
                Logs.logTaskRun(
                    workerTaskRunning.getTaskRun(),
                    Level.ERROR,
                    "Unable to resubmit WorkerTask.",
                    e
                );
            }
        }
    }

    /**
     * The worker holding the trigger is gone: delete its running entry and notify the scheduler,
     * which owns resubmission. Re-emitting the persisted job from here would replay a stale
     * trigger definition and ignore a disabled or deleted state.
     */
    private void notifyTriggerWorkerLost(TransactionContext txContext, WorkerTriggerRunning workerTriggerRunning) {
        TriggerId triggerId = TriggerId.of(
            workerTriggerRunning.getData().tenantId(),
            workerTriggerRunning.getData().namespace(),
            workerTriggerRunning.getData().flowId(),
            workerTriggerRunning.getTrigger().getId()
        );
        workerJobRunningStateStore.deleteByKey(txContext, workerTriggerRunning.uid());
        triggerEventQueue.send(new TriggerWorkerLost(triggerId, workerTriggerRunning.getWorkerInstance().uid()));
        Logs.logTrigger(
            triggerId,
            Level.WARN,
            "Worker '{}' lost, notifying the scheduler.",
            workerTriggerRunning.getWorkerInstance().uid()
        );
    }
}
