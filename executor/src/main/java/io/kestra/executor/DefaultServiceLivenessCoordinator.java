package io.kestra.executor;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.lock.Lock;
import io.kestra.core.lock.LockService;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.runners.*;
import io.kestra.core.server.*;
import io.kestra.core.services.LogService;
import io.kestra.core.services.SkipExecutionService;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static io.kestra.core.server.Service.ServiceState.*;

/**
 * Responsible for coordinating the state of all service instances.
 *
 * @see ServiceInstance
 */
@Slf4j
@Singleton
@Requires(property = "kestra.server-type", pattern = "(EXECUTOR|STANDALONE)")
public class DefaultServiceLivenessCoordinator extends AbstractServiceLivenessTask {

     private static final int DEFAULT_SCHEDULE_JITTER_MAX_MS = 500;

    private static final String DEFAULT_REASON_FOR_DISCONNECTED =
        "The service was detected as non-responsive after the session timeout. " +
        "Service transitioned to the 'DISCONNECTED' state.";

    private static final String DEFAULT_REASON_FOR_NOT_RUNNING =
        "The service was detected as non-responsive or terminated after termination grace period. " +
        "Service transitioned to the 'NOT_RUNNING' state.";

    private static final String TASK_NAME = "service-liveness-coordinator-task";

    private final ServiceLivenessStore store;
    private final ServiceRegistry serviceRegistry;
    private final ServiceLivenessUpdater serviceLivenessUpdater;
    private final ServiceInstanceRepositoryInterface serviceInstanceRepository;
    private final Duration purgeRetention;

    private final LockService lockService;
    private final SkipExecutionService skipExecutionService;
    private final QueueInterface<WorkerJob> workerJobQueue;
    private final WorkerJobRunningStateStore workerJobRunningStateStore;
    private final LogService logService;
    private final MetricRegistry metricRegistry;

    // mutable for testing purpose
    String serverId = ServerInstance.INSTANCE_ID;

    @VisibleForTesting
    void setServerInstance(String serverId) {
        this.serverId = serverId;
    }

    /**
     * Creates a new {@link DefaultServiceLivenessCoordinator} instance.
     *
     * @param store        The {@link ServiceInstanceRepositoryInterface}.
     * @param serverConfig The server configuration.
     */
    @Inject
    public DefaultServiceLivenessCoordinator(final ServiceLivenessStore store,
                                             final ServiceRegistry serviceRegistry,
                                             final ServiceLivenessUpdater serviceLivenessUpdater,
                                             final ServiceInstanceRepositoryInterface serviceInstanceRepository,
                                             final LockService lockService,
                                             final SkipExecutionService skipExecutionService,
                                             final @Named(QueueFactoryInterface.WORKERJOB_NAMED) QueueInterface<WorkerJob> workerJobQueue,
                                             final WorkerJobRunningStateStore workerJobRunningStateStore,
                                             final LogService logService,
                                             final ServerConfig serverConfig,
                                             final MetricRegistry metricRegistry,
                                             @Value("${kestra.server.service.purge.retention}") final Duration purgeRetention) {
        super(TASK_NAME, serverConfig);
        this.serviceRegistry = serviceRegistry;
        this.serviceLivenessUpdater = serviceLivenessUpdater;
        this.serviceInstanceRepository = serviceInstanceRepository;
        this.store = store;
        this.skipExecutionService = skipExecutionService;
        this.workerJobQueue = workerJobQueue;
        this.workerJobRunningStateStore = workerJobRunningStateStore;
        this.logService = logService;
        this.lockService = lockService;
        this.metricRegistry = metricRegistry;
        this.purgeRetention = purgeRetention;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected void onSchedule(Instant now) throws Exception {
        if (Optional.ofNullable(serviceRegistry.get(ServiceType.EXECUTOR))
            .filter(service -> service.instance().is(RUNNING))
            .isEmpty()) {
            log.debug(
                "The liveness coordinator task was temporarily disabled. Executor is not yet in the RUNNING state."
            );
            return;
        }

        // Update all RUNNING but non-responding services to DISCONNECTED.
        handleAllNonRespondingServices(now);

        // Handle all workers which are not in a RUNNING state.
        handleAllWorkersForUncleanShutdown(now);

        // Update all services one of the TERMINATED states to NOT_RUNNING.
        handleAllServicesForTerminatedStates(now);

        // Update all services in NOT_RUNNING to EMPTY (a.k.a soft delete).
        handleAllServiceInNotRunningState();

        maybeDetectAndLogNewConnectedServices();
    }

    /**
     * Handles all worker services which are shutdown or considered to be terminated.
     * <p>
     * This method may re-submit tasks is necessary.
     *
     * @param now the time of the execution.
     */
    protected void handleAllWorkersForUncleanShutdown(Instant now) {
            serviceInstanceRepository.processAllNonRunningInstances((txContext, serviceInstance) -> {
                // List of workers for which we don't know the actual state of tasks executions.
                // Re-emit all WorkerJobs for unclean workers
                if (isUncleanShutdownService(serviceInstance, now)) {
                    if (serviceInstance.config().workerTaskRestartStrategy().isRestartable()) {
                        log.info("Trigger task restart for non-responding worker after termination grace period: {}.", serviceInstance.uid());
                        reEmitWorkerJobsForWorker(txContext, serviceInstance.uid());
                    }
                }

                // Transit GRACEFUL and UNCLEAN SHUTDOWN worker to NOT_RUNNING.
                if (serviceInstance.is(Service.ServiceState.TERMINATED_GRACEFULLY)) {
                    serviceInstanceRepository.mayTransitServiceTo(txContext,
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
        serviceInstanceRepository.processInstanceInStates(allRunningStates(), (txContext, serviceInstance) -> {
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
                if (serviceInstance.is(ServiceType.WORKER) &&
                    serviceInstance.config().workerTaskRestartStrategy().equals(WorkerTaskRestartStrategy.IMMEDIATELY)) {
                    log.info("Trigger task restart for non-responding worker after timeout: {}.", serviceInstance.uid());
                    reEmitWorkerJobsForWorker(txContext, serviceInstance.uid());
                }

                // Eventually release all owned locks
                List<Lock> released = lockService.releaseAllLocks(serviceInstance.uid());
                released.forEach(l -> log.info("Released lock {} for non-responding service instance {}", IdUtils.fromParts(l.getCategory(), l.getId()), serviceInstance.uid()));
            }
        });
    }

    @Scheduled(initialDelay = "${kestra.server.service.purge.initial-delay}", fixedDelay = "${kestra.server.service.purge.fixed-delay}")
    public void purgeEmptyInstances() {
        int purged = serviceInstanceRepository.purgeEmptyInstances(Instant.now().minus(purgeRetention));
        log.info("Purged {} service instances", purged);
    }

    private void reEmitWorkerJobsForWorker(final TransactionContext txContext, final String id) {
        metricRegistry.counter(MetricRegistry.METRIC_EXECUTOR_WORKER_JOB_RESUBMIT_COUNT, MetricRegistry.METRIC_EXECUTOR_WORKER_JOB_RESUBMIT_COUNT_DESCRIPTION)
            .increment();

        workerJobRunningStateStore.processWorkerJobsForDeadWorker(txContext, id, (txContext2, workerJobRunning) -> {
            resubmitWorkerJobRunning(txContext2, workerJobRunning);
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
                log.warn("Detected non-responding service [id={}, type={}, hostname={}] after timeout ({}ms).",
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
            .forEach(instance -> safelyUpdate(instance, Service.ServiceState.INACTIVE, null));
    }

    private void handleAllServicesForTerminatedStates(final Instant now) {
        store
            .findAllInstancesInStates(Set.of(DISCONNECTED, TERMINATING, TERMINATED_GRACEFULLY, TERMINATED_FORCED))
            .stream()
            .filter(instance -> !instance.is(ServiceType.WORKER)) // WORKERS are handle above.
            .filter(instance -> instance.isTerminationGracePeriodElapsed(now))
            .peek(instance -> maybeLogNonRespondingAfterTerminationGracePeriod(instance, now))
            .forEach(instance -> safelyUpdate(instance, NOT_RUNNING, DEFAULT_REASON_FOR_NOT_RUNNING));
    }

    private void maybeDetectAndLogNewConnectedServices() {
        if (log.isDebugEnabled()) {
            // Log the newly-connected services (useful for troubleshooting).
            store.findAllInstancesInStates(Set.of(CREATED, RUNNING))
                .stream()
                .filter(instance -> instance.createdAt().isAfter(lastScheduledExecution()))
                .forEach(instance -> {
                    log.debug("Detected new service [id={}, type={}, hostname={}] (started at: {}).",
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
            log.error("Unexpected error while service [id={}, type={}, hostname={}] transition from {} to {}.",
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
            log.warn("Detected non-responding service [id={}, type={}, hostname={}] after termination grace period ({}ms).",
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
            if (skipExecutionService.skipExecution(workerTaskRunning.getTaskRun())) {
                // if the execution is skipped, we remove the workerTaskRunning and skip its resubmission
                log.warn("Skipping execution {}", workerTaskRunning.getTaskRun().getExecutionId());
                workerJobRunningStateStore.deleteByKey(txContext, workerTaskRunning.uid());
            } else {
                try {
                    workerJobQueue.emit(workerTaskRunning.getWorkerInstance().workerGroup(), WorkerTask.builder()
                        .taskRun(workerTaskRunning.getTaskRun().onRunningResend())
                        .task(workerTaskRunning.getTask())
                        .runContext(workerTaskRunning.getRunContext())
                        .build()
                    );
                    logService.logTaskRun(
                        workerTaskRunning.getTaskRun(),
                        Level.WARN,
                        "Resubmit WorkerTask."
                    );
                } catch (QueueException e) {
                    logService.logTaskRun(
                        workerTaskRunning.getTaskRun(),
                        Level.ERROR,
                        "Unable to resubmit WorkerTask.",
                        e
                    );
                }
            }
        }

        // WorkerTriggerRunning
        if (workerJobRunning instanceof WorkerTriggerRunning workerTriggerRunning) {
            try {
                workerJobQueue.emit(workerTriggerRunning.getWorkerInstance().workerGroup(), WorkerTrigger.builder()
                    .trigger(workerTriggerRunning.getTrigger())
                    .conditionContext(workerTriggerRunning.getConditionContext())
                    .triggerContext(workerTriggerRunning.getTriggerContext())
                    .build());
                logService.logTrigger(
                    workerTriggerRunning.getTriggerContext(),
                    Level.WARN,
                    "Re-emitting WorkerTrigger."
                );
            } catch (QueueException e) {
                logService.logTrigger(
                    workerTriggerRunning.getTriggerContext(),
                    Level.ERROR,
                    "Unable to re-emit WorkerTrigger.",
                    e
                );
            }
        }
    }
}
