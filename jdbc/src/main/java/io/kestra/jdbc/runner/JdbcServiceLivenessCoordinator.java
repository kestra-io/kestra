package io.kestra.jdbc.runner;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.server.AbstractServiceLivenessCoordinator;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.Service.ServiceState;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceRegistry;
import io.kestra.core.server.ServiceType;
import io.kestra.core.server.WorkerTaskRestartStrategy;
import io.kestra.jdbc.repository.AbstractJdbcServiceInstanceRepository;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

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
    private final Duration purgeRetention;

    /**
     * Creates a new {@link JdbcServiceLivenessCoordinator} instance.
     *
     * @param serviceInstanceRepository The {@link AbstractJdbcServiceInstanceRepository}.
     * @param serverConfig              The server liveness configuration.
     */
    @Inject
    public JdbcServiceLivenessCoordinator(final AbstractJdbcServiceInstanceRepository serviceInstanceRepository,
                                          final ServiceRegistry serviceRegistry,
                                          final ServerConfig serverConfig,
                                          @Value("${kestra.server.service.purge.retention}") final Duration purgeRetention) {
        super(serviceInstanceRepository, serviceRegistry, serverConfig);
        this.serviceInstanceRepository = serviceInstanceRepository;
        this.purgeRetention = purgeRetention;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected void onSchedule(final Instant now) throws Exception {
        if (executor.get() == null) return; // only True during startup
        super.onSchedule(now);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected void handleAllWorkersForUncleanShutdown(Instant now) {
        serviceInstanceRepository.transaction(configuration -> {
            final List<ServiceInstance> nonRunningWorkers = serviceInstanceRepository
                .findAllNonRunningInstances(configuration, true)
                .stream()
                .filter(instance -> instance.is(ServiceType.WORKER))
                .toList();

            // List of workers for which we don't know the actual state of tasks executions.
            final List<ServiceInstance> uncleanShutdownWorkers = filterAllUncleanShutdownServices(nonRunningWorkers, now);

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
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected void update(ServiceInstance instance, ServiceState state, String reason) {
        serviceInstanceRepository.update(instance, state, reason);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected void handleAllNonRespondingServices(Instant now) {
        serviceInstanceRepository.transaction(configuration -> {
            // Retrieves all services that are supposed to be running.
            List<ServiceInstance> allRunningInstances = serviceInstanceRepository.findAllInstancesInStates(configuration, allRunningStates(), true);

            // Detect and handle non-responding services.
            List<ServiceInstance> nonRespondingServices = filterAllNonRespondingServices(allRunningInstances, now);

            // Attempt to transit all non-responding services to DISCONNECTED.
            nonRespondingServices.forEach(instance -> serviceInstanceRepository.mayTransitServiceTo(
                configuration,
                instance,
                ServiceState.DISCONNECTED,
                DEFAULT_REASON_FOR_DISCONNECTED
            ));

            // Eventually restart workers tasks
            List<String> workerIdsHavingTasksToRestart = nonRespondingServices.stream()
                .filter(instance -> instance.is(ServiceType.WORKER))
                .filter(instance -> instance.config().workerTaskRestartStrategy().equals(WorkerTaskRestartStrategy.IMMEDIATELY))
                .map(ServiceInstance::uid)
                .toList();

            if (!workerIdsHavingTasksToRestart.isEmpty()) {
                log.info("Trigger task restart for non-responding workers after timeout: {}.", workerIdsHavingTasksToRestart);
                executor.get().reEmitWorkerJobsForWorkers(configuration, workerIdsHavingTasksToRestart);
            }
        });
    }

    @Scheduled(initialDelay = "${kestra.server.service.purge.initial-delay}", fixedDelay = "${kestra.server.service.purge.fixed-delay}")
    public void purgeEmptyInstances() {
        int purged = serviceInstanceRepository.purgeEmptyInstances(Instant.now().minus(purgeRetention));
        log.info("Purged {} service instances", purged);
    }


    synchronized void setExecutor(final JdbcExecutor executor) {
        this.executor.set(executor);
    }

    @VisibleForTesting
    void setServerInstance(final String serverId) {
        this.serverId = serverId;
    }
}
