package io.kestra.core.server;

import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static io.kestra.core.server.Service.ServiceState.*;

/**
 * Base class for coordinating service liveness.
 */
@Introspected
@Slf4j
public abstract class AbstractServiceLivenessCoordinator extends AbstractServiceLivenessTask {

    private final static int DEFAULT_SCHEDULE_JITTER_MAX_MS = 500;

    protected static String DEFAULT_REASON_FOR_DISCONNECTED =
        "The service was detected as non-responsive after the session timeout. " +
        "Service transitioned to the 'DISCONNECTED' state.";

    protected static String DEFAULT_REASON_FOR_NOT_RUNNING =
        "The service was detected as non-responsive or terminated after termination grace period. " +
        "Service transitioned to the 'NOT_RUNNING' state.";

    private static final String TASK_NAME = "service-liveness-coordinator-task";

    protected final ServiceLivenessStore store;

    // mutable for testing purpose
    protected String serverId = ServerInstance.INSTANCE_ID;

    /**
     * Creates a new {@link AbstractServiceLivenessCoordinator} instance.
     *
     * @param store        The {@link ServiceInstanceRepositoryInterface}.
     * @param serverConfig The server configuration.
     */
    @Inject
    public AbstractServiceLivenessCoordinator(final ServiceLivenessStore store,
                                              final ServerConfig serverConfig) {
        super(TASK_NAME, serverConfig);
        this.store = store;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected void onSchedule(Instant now) throws Exception {
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
     * Handles all unresponsive services and update their status to disconnected.
     * <p>
     * This method may re-submit tasks is necessary.
     *
     * @param now the time of the execution.
     */
    protected abstract void handleAllNonRespondingServices(final Instant now);

    /**
     * Handles all worker services which are shutdown or considered to be terminated.
     * <p>
     * This method may re-submit tasks is necessary.
     *
     * @param now the time of the execution.
     */
    protected abstract void handleAllWorkersForUncleanShutdown(final Instant now);

    protected abstract void update(final ServiceInstance instance,
                                   final Service.ServiceState state,
                                   final String reason) ;

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

    protected List<ServiceInstance> filterAllUncleanShutdownServices(final List<ServiceInstance> instances,
                                                                     final Instant now) {
        // List of services for which we don't know the actual state
        final List<ServiceInstance> uncleanShutdownServices = new ArrayList<>();

        // ...all services that have transitioned to DISCONNECTED or TERMINATING for more than terminationGracePeriod.
        uncleanShutdownServices.addAll(instances.stream()
            .filter(nonRunning -> nonRunning.state().isDisconnectedOrTerminating())
            .filter(disconnectedOrTerminating -> disconnectedOrTerminating.isTerminationGracePeriodElapsed(now))
            .peek(instance -> maybeLogNonRespondingAfterTerminationGracePeriod(instance, now))
            .toList()
        );
        // ...all services that have transitioned to TERMINATED_FORCED.
        uncleanShutdownServices.addAll(instances.stream()
            .filter(nonRunning -> nonRunning.is(Service.ServiceState.TERMINATED_FORCED))
            .toList()
        );
        return uncleanShutdownServices;
    }

    protected List<ServiceInstance> filterAllNonRespondingServices(final List<ServiceInstance> instances,
                                                                   final Instant now) {
        return instances.stream()
            .filter(instance -> instance.config().liveness().enabled())
            .filter(instance -> instance.isSessionTimeoutElapsed(now))
            // exclude any service running on the same server as the executor, to prevent the latter from shutting down.
            .filter(instance -> !instance.server().id().equals(serverId))
            // only keep services eligible for liveness probe
            .filter(instance -> {
                final Instant minInstantForLivenessProbe = now.minus(instance.config().liveness().initialDelay());
                return instance.createdAt().isBefore(minInstantForLivenessProbe);
            })
            // warn
            .peek(instance -> log.warn("Detected non-responding service [id={}, type={}, hostname={}] after timeout ({}ms).",
                instance.uid(),
                instance.type(),
                instance.server().hostname(),
                now.toEpochMilli() - instance.updatedAt().toEpochMilli()
            ))
            .toList();

    }

    protected void handleAllServiceInNotRunningState() {
        // Soft delete all services which are NOT_RUNNING anymore.
        store.findAllInstancesInStates(Set.of(Service.ServiceState.NOT_RUNNING))
            .forEach(instance -> safelyUpdate(instance, Service.ServiceState.EMPTY, null));
    }

    protected void handleAllServicesForTerminatedStates(final Instant now) {
        store
            .findAllInstancesInStates(Set.of(DISCONNECTED, TERMINATING, TERMINATED_GRACEFULLY, TERMINATED_FORCED))
            .stream()
            .filter(instance -> !instance.is(Service.ServiceType.WORKER)) // WORKERS are handle above.
            .filter(instance -> instance.isTerminationGracePeriodElapsed(now))
            .peek(instance -> maybeLogNonRespondingAfterTerminationGracePeriod(instance, now))
            .forEach(instance -> safelyUpdate(instance, NOT_RUNNING, DEFAULT_REASON_FOR_NOT_RUNNING));
    }

    protected void maybeDetectAndLogNewConnectedServices() {
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

    protected void safelyUpdate(final ServiceInstance instance,
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

    protected static void maybeLogNonRespondingAfterTerminationGracePeriod(final ServiceInstance instance,
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
}
