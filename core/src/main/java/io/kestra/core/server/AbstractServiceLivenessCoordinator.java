package io.kestra.core.server;

import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

import static io.kestra.core.server.Service.ServiceState.CREATED;
import static io.kestra.core.server.Service.ServiceState.RUNNING;

/**
 * Base class for coordinating service liveness.
 */
@Introspected
@Slf4j
public abstract class AbstractServiceLivenessCoordinator extends AbstractServiceLivenessTask {

    private final static int DEFAULT_SCHEDULE_JITTER_MAX_MS = 500;

    private static final String TASK_NAME = "service-liveness-coordinator-task";

    protected final ServiceInstanceRepositoryInterface serviceInstanceRepository;

    // mutable for testing purpose
    protected String serverId = ServerInstance.INSTANCE_ID;

    /**
     * Creates a new {@link AbstractServiceLivenessCoordinator} instance.
     *
     * @param serviceInstanceRepository The {@link ServiceInstanceRepositoryInterface}.
     * @param serverConfig              The server configuration.
     */
    @Inject
    public AbstractServiceLivenessCoordinator(final ServiceInstanceRepositoryInterface serviceInstanceRepository,
                                              final ServerConfig serverConfig) {
        super(TASK_NAME, serverConfig);
        this.serviceInstanceRepository = serviceInstanceRepository;
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

    /**
     * Transitions to the DISCONNECTED state all non-local services for which liveness
     * is enabled and are detected as non-responsive .
     *
     * @param now   the instant.
     */
    protected void transitionAllNonRespondingService(final Instant now) {

        // Detect and handle non-responding services.
        List<ServiceInstance> nonRespondingServices = serviceInstanceRepository
            // gets all non-responding services.
            .findAllTimeoutRunningInstances(now)
            .stream()
            // keep only services with liveness enabled.
            .filter(instance -> instance.config().liveness().enabled())
            // exclude any service running on the same server as the executor, to prevent the latter from shutting down.
            .filter(instance -> !instance.server().id().equals(serverId))
            // only keep services eligible for liveness probe
            .filter(instance -> {
                final Instant minInstantForLivenessProbe = now.minus(instance.config().liveness().initialDelay());
                return instance.createdAt().isBefore(minInstantForLivenessProbe);
            })
            // warn
            .peek(instance -> log.warn("Detected non-responding service [id={}, type={}, hostname={}] after timeout ({}ms).",
                instance.id(),
                instance.type(),
                instance.server().hostname(),
                now.toEpochMilli() - instance.updatedAt().toEpochMilli()
            ))
            .toList();

        // Attempt to transit all non-responding services to DISCONNECTED.
        nonRespondingServices.forEach(instance -> this.safelyTransitionServiceTo(
            instance,
            Service.ServiceState.DISCONNECTED,
            "The service was detected as non-responsive after the session timeout. Service was transitioned to the 'DISCONNECTED' state."
        ));
    }

    protected void mayDetectAndLogNewConnectedServices() {
        if (log.isInfoEnabled()) {
            // Log the newly-connected services (useful for troubleshooting).
            serviceInstanceRepository.findAllInstancesInStates(List.of(CREATED, RUNNING))
                .stream()
                .filter(instance -> instance.createdAt().isAfter(lastScheduledExecution()))
                .forEach(instance -> {
                    log.info("Detected new service [id={}, type={}, hostname={}] (started at: {}).",
                        instance.id(),
                        instance.type(),
                        instance.server().hostname(),
                        instance.createdAt()
                    );
                });
        }
    }

    protected void safelyTransitionServiceTo(final ServiceInstance instance,
                                             final Service.ServiceState state,
                                             final String reason) {
        try {
            serviceInstanceRepository.mayTransitionServiceTo(instance, state, reason);
        } catch (Exception e) {
            // Log and ignore exception - it's safe to ignore error because the run() method is supposed to schedule at fix rate.
            log.error("Unexpected error while service [id={}, type={}, hostname={}] transition from {} to {}. Error: {}",
                instance.id(),
                instance.type(),
                instance.server().hostname(),
                instance.state(),
                state,
                e.getMessage()
            );
        }
    }
}
