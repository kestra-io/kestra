package io.kestra.jdbc.runner;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.server.AbstractServiceLivenessCoordinator;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.Service;
import io.kestra.core.server.Service.ServiceState;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.WorkerTaskRestartStrategy;
import io.kestra.jdbc.repository.AbstractJdbcServiceInstanceRepository;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.kestra.core.server.Service.ServiceState.DISCONNECTED;
import static io.kestra.core.server.Service.ServiceState.NOT_RUNNING;
import static io.kestra.core.server.Service.ServiceState.TERMINATED_FORCED;
import static io.kestra.core.server.Service.ServiceState.TERMINATED_GRACEFULLY;
import static io.kestra.core.server.Service.ServiceState.TERMINATING;
import static io.kestra.core.server.Service.ServiceState.allRunningStates;

/**
 * Responsible for coordinating the state of all service instances.
 *
 * @see ServiceInstance
 */
@Singleton
@JdbcRunnerEnabled
@Requires(property = "kestra.server-type", pattern = "(EXECUTOR|STANDALONE)")
public final class JdbcServiceLivenessCoordinator extends AbstractServiceLivenessCoordinator {

    private final static Logger log = LoggerFactory.getLogger(JdbcServiceLivenessCoordinator.class);

    private final AtomicReference<JdbcExecutor> executor = new AtomicReference<>();
    private final AbstractJdbcServiceInstanceRepository serviceInstanceRepository;

    /**
     * Creates a new {@link JdbcServiceLivenessCoordinator} instance.
     *
     * @param serviceInstanceRepository The {@link AbstractJdbcServiceInstanceRepository}.
     * @param serverConfig              The server liveness configuration.
     */
    @Inject
    public JdbcServiceLivenessCoordinator(final AbstractJdbcServiceInstanceRepository serviceInstanceRepository,
                                          final ServerConfig serverConfig) {
        super(serviceInstanceRepository, serverConfig);
        this.serviceInstanceRepository = serviceInstanceRepository;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected void onSchedule(final Instant now) {
        if (executor.get() == null) return; // only True during startup

        // Transition all RUNNING but non-responding services to DISCONNECTED.
        serviceInstanceRepository.transaction(configuration -> {
            List<ServiceInstance> instances = serviceInstanceRepository.findAllInstancesInStates(configuration, allRunningStates(), true);
            List<ServiceInstance> nonRespondingServices = filterAllNonRespondingServices(instances, now);

            nonRespondingServices.forEach(instance -> serviceInstanceRepository.mayTransitServiceTo(
                configuration,
                instance,
                Service.ServiceState.DISCONNECTED,
                DEFAULT_REASON_FOR_DISCONNECTED
            ));

            // Eventually restart workers tasks
            List<String> workerIdsHavingTasksToRestart = nonRespondingServices.stream()
                .filter(instance -> instance.is(Service.ServiceType.WORKER))
                .filter(instance -> instance.config().workerTaskRestartStrategy().equals(WorkerTaskRestartStrategy.IMMEDIATELY))
                .map(ServiceInstance::uid)
                .toList();

            if (!workerIdsHavingTasksToRestart.isEmpty()) {
                log.info("Trigger task restart for non-responding workers after timeout: {}.", workerIdsHavingTasksToRestart);
                executor.get().reEmitWorkerJobsForWorkers(configuration, workerIdsHavingTasksToRestart);
            }
        });

        // Finds all workers which are not in a RUNNING state.
        serviceInstanceRepository.transaction(configuration -> {
            final List<ServiceInstance> nonRunningWorkers = serviceInstanceRepository
                .findAllNonRunningInstances(configuration, true)
                .stream()
                .filter(instance -> instance.is(Service.ServiceType.WORKER))
                .toList();

            // List of workers for which we don't know the actual state of tasks executions.
            final List<ServiceInstance> uncleanShutdownWorkers = new ArrayList<>();

            // ...all workers that have transitioned to DISCONNECTED or TERMINATING for more than terminationGracePeriod).
            uncleanShutdownWorkers.addAll(nonRunningWorkers.stream()
                .filter(nonRunning -> nonRunning.state().isDisconnectedOrTerminating())
                .filter(disconnectedOrTerminating -> disconnectedOrTerminating.isTerminationGracePeriodElapsed(now))
                .peek(instance -> mayLogNonRespondingAfterTerminationGracePeriod(instance, now))
                .toList()
            );
            // ...all workers that have transitioned to TERMINATED_FORCED.
            uncleanShutdownWorkers.addAll(nonRunningWorkers.stream()
                .filter(nonRunning -> nonRunning.is(ServiceState.TERMINATED_FORCED))
                .toList()
            );

            // Re-emit all WorkerJobs for unclean workers
            if (!uncleanShutdownWorkers.isEmpty()) {
                List<String> ids = uncleanShutdownWorkers.stream()
                    .filter(instance -> instance.config().workerTaskRestartStrategy().isRestartable())
                    .map(ServiceInstance::uid)
                    .toList();
                if (!ids.isEmpty()) {
                    log.info("Trigger task restart for non-responding workers after termination grace period: {}.", ids);
                    executor.get().reEmitWorkerJobsForWorkers(configuration, ids);
                }
            }

            // Transit all GRACEFUL AND UNCLEAN SHUTDOWN workers to NOT_RUNNING.
            Stream<ServiceInstance> cleanShutdownWorkers = nonRunningWorkers.stream()
                .filter(nonRunning -> nonRunning.is(ServiceState.TERMINATED_GRACEFULLY));
            Stream.concat(cleanShutdownWorkers, uncleanShutdownWorkers.stream()).forEach(
                instance -> serviceInstanceRepository.mayTransitServiceTo(configuration,
                    instance,
                    ServiceState.NOT_RUNNING,
                    DEFAULT_REASON_FOR_NOT_RUNNING
                )
            );
        });

        // Transition all TERMINATED services to NOT_RUNNING.
        serviceInstanceRepository
            .findAllInstancesInStates(Set.of(DISCONNECTED, TERMINATING, TERMINATED_GRACEFULLY, TERMINATED_FORCED)).stream()
            .filter(instance -> !instance.is(Service.ServiceType.WORKER)) // WORKERS are handle above.
            .filter(instance -> instance.isTerminationGracePeriodElapsed(now))
            .peek(instance -> mayLogNonRespondingAfterTerminationGracePeriod(instance, now))
            .forEach(instance -> safelyTransitionServiceTo(instance, NOT_RUNNING, DEFAULT_REASON_FOR_NOT_RUNNING));

        // Soft delete all services which are NOT_RUNNING anymore.
        serviceInstanceRepository.findAllInstancesInState(ServiceState.NOT_RUNNING)
            .forEach(instance -> safelyTransitionServiceTo(instance, ServiceState.EMPTY, null));

        mayDetectAndLogNewConnectedServices();
    }

    private static void mayLogNonRespondingAfterTerminationGracePeriod(final ServiceInstance instance,
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


    synchronized void setExecutor(final JdbcExecutor executor) {
        this.executor.set(executor);
    }

    @VisibleForTesting
    void setServerInstance(final String serverId) {
        this.serverId = serverId;
    }
}
