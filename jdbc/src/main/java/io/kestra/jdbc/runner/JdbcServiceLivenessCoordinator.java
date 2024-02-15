package io.kestra.jdbc.runner;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.server.AbstractServiceLivenessCoordinator;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.Service;
import io.kestra.core.server.Service.ServiceState;
import io.kestra.core.server.ServiceInstance;
import io.kestra.jdbc.repository.AbstractJdbcServiceInstanceRepository;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.kestra.core.server.Service.ServiceState.*;

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
        transitionAllNonRespondingService(now);

        // Finds all workers which are not in a RUNNING state.
        serviceInstanceRepository.transaction(configuration -> {
            List<ServiceInstance> nonRunningWorkers = serviceInstanceRepository
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
                .peek(instance -> {
                    log.warn("Detected non-responding service [id={}, type={}, hostname={}] after termination grace period ({}ms).",
                        instance.id(),
                        instance.type(),
                        instance.server().hostname(),
                        now.toEpochMilli() - instance.updatedAt().toEpochMilli()
                    );
                })
                .toList()
            );
            // ...all workers that have transitioned to TERMINATED_FORCED.
            uncleanShutdownWorkers.addAll(nonRunningWorkers.stream()
                .filter(nonRunning -> nonRunning.is(ServiceState.TERMINATED_FORCED))
                .toList()
            );

            // Re-emit all WorkerJobs for unclean workers
            if (!uncleanShutdownWorkers.isEmpty()) {
                List<String> ids = uncleanShutdownWorkers.stream().map(ServiceInstance::id).toList();
                executor.get().reEmitWorkerJobsForWorkers(configuration, ids);
            }

            // Transit all GRACEFUL AND UNCLEAN SHUTDOWN workers to NOT_RUNNING.
            Stream<ServiceInstance> cleanShutdownWorkers = nonRunningWorkers.stream()
                .filter(nonRunning -> nonRunning.is(ServiceState.TERMINATED_GRACEFULLY));
            Stream.concat(cleanShutdownWorkers, uncleanShutdownWorkers.stream()).forEach(
                instance -> serviceInstanceRepository.mayTransitServiceTo(configuration,
                    instance,
                    ServiceState.NOT_RUNNING,
                    "The worker was detected as non-responsive after termination grace period. Service was transitioned to the 'NOT_RUNNING' state."
                )
            );
        });

        // Transition all TERMINATED services to NOT_RUNNING.
        serviceInstanceRepository
            .findAllInstancesInStates(List.of(DISCONNECTED, TERMINATING, TERMINATED_GRACEFULLY, TERMINATED_FORCED)).stream()
            .filter(instance -> !instance.is(Service.ServiceType.WORKER)) // WORKERS are handle above.
            .filter(instance ->instance.isTerminationGracePeriodElapsed(now))
            .forEach(instance -> safelyTransitionServiceTo(instance, NOT_RUNNING, null));


        // Soft delete all services which are NOT_RUNNING anymore.
        serviceInstanceRepository.findAllInstancesInState(ServiceState.NOT_RUNNING)
            .forEach(instance -> safelyTransitionServiceTo(instance, ServiceState.EMPTY, null));

        mayDetectAndLogNewConnectedServices();
    }

    synchronized void setExecutor(final JdbcExecutor executor) {
        this.executor.set(executor);
    }

    @VisibleForTesting
    void setServerInstance(final String serverId) {
        this.serverId = serverId;
    }
}
