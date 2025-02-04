package io.kestra.core.server;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.server.ServiceStateTransition.Result;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import static io.kestra.core.server.ServiceLivenessManager.OnStateTransitionFailureCallback.NOOP;

/**
 * Service responsible for managing the state of local Kestra's Services.
 * Moreover, this class periodically send state updates (a.k.a. heartbeats) to indicate service's liveness.
 */
public class ServiceLivenessManager extends AbstractServiceLivenessTask {

    private static final Logger log = LoggerFactory.getLogger(ServiceLivenessManager.class);

    private static final String TASK_NAME = "service-liveness-manager-task";
    private final LocalServiceStateFactory localServiceStateFactory;
    private final ServiceLivenessUpdater serviceLivenessUpdater;
    private final ReentrantLock stateLock = new ReentrantLock();
    protected final OnStateTransitionFailureCallback onStateTransitionFailureCallback;
    private final ServerInstanceFactory serverInstanceFactory;
    private final ServiceRegistry serviceRegistry;

    private Instant lastSucceedStateUpdated;

    public ServiceLivenessManager(final ServerConfig configuration,
                                  final ServiceRegistry serviceRegistry,
                                  final LocalServiceStateFactory localServiceStateFactory,
                                  final ServerInstanceFactory serverInstanceFactory,
                                  final ServiceLivenessUpdater serviceLivenessUpdater,
                                  final OnStateTransitionFailureCallback onStateTransitionFailureCallback) {
        super(TASK_NAME, configuration);
        this.serviceRegistry = serviceRegistry;
        this.localServiceStateFactory = localServiceStateFactory;
        this.serverInstanceFactory = serverInstanceFactory;
        this.serviceLivenessUpdater = serviceLivenessUpdater;
        this.onStateTransitionFailureCallback = onStateTransitionFailureCallback;
    }

    /**
     * Handles the given state change event.
     *
     * @param event The state change event.
     */
    public void onServiceStateChangeEvent(final ServiceStateChangeEvent event) {

        final Service.ServiceState newState = event.getService().getState();

        if (newState == null) {
            return; // invalid service event.
        }

        // Check whether the state for this service is updatable.
        // A service (e.g., Worker) is not updatable when its state has already been transitioned to
        // a completed state (e.g., NOT_RUNNING) by an external service (e.g. Executor).
        LocalServiceState holder = serviceRegistry.get(event.getService().getType());
        if (holder != null && !holder.isStateUpdatable().get()) {
            ServiceInstance instance = holder.instance();
            log.debug(
                "[Service id={}, type={}, hostname={}] Service state is not updatable. StateChangeEvent[{}] skipped.",
                instance.uid(),
                instance.type(),
                instance.server().hostname(),
                instance.state()
            );
            return;
        }

        switch (newState) {
            case CREATED:
                onCreateState(event);
                break;
            case RUNNING, TERMINATING, TERMINATED_GRACEFULLY, TERMINATED_FORCED, MAINTENANCE:
                updateServiceInstanceState(Instant.now(), event.getService(), newState, NOOP);
                break;
            default:
                log.warn("Unsupported service state: {}. Ignored.", newState);
        }
    }

    /**
     * Handles {@link Service.ServiceState#CREATED}.
     */
    private void onCreateState(final ServiceStateChangeEvent event) {
        Service service = event.getService();
        LocalServiceState localServiceState = localServiceStateFactory.newLocalServiceState(
            service,
            event.properties()
        );
        final ServiceInstance instance = localServiceState.instance();

        serviceLivenessUpdater.update(instance);
        this.serviceRegistry.register(localServiceState.with(instance));
        if (log.isDebugEnabled()) {
            log.debug("[Service id={}, type='{}', hostname='{}'] Connected.",
                instance.uid(),
                instance.type(),
                instance.server().hostname()
            );
        }
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected Duration getScheduleInterval() {
        return serverConfig.liveness().heartbeatInterval();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    protected void onSchedule(final Instant now) {

        if (serviceRegistry.isEmpty()) {
            log.trace("No service registered yet. Skip service state update.");
            return;
        }

        // Try to update the state of each service.
        serviceRegistry.all().stream()
            .filter(localServiceState -> localServiceState.isStateUpdatable().get())
            .forEach(localServiceState -> {
                final long start = System.currentTimeMillis();
                final Service service = localServiceState.service();

                // Execute the before hook.
                if (!beforeScheduledStateUpdate(now, service, localServiceState.instance())) {
                    return;
                }

                // Execute state update for current service (i.e., heartbeat).
                ServiceInstance instance = updateServiceInstanceState(now, service, null, onStateTransitionFailureCallback);
                if (log.isTraceEnabled() && instance != null) {
                    log.trace("[Service id={}, type={}, hostname='{}'] Completed scheduled state update: '{}' ({}ms).",
                        instance.uid(),
                        instance.type(),
                        instance.server().hostname(),
                        instance.state(),
                        System.currentTimeMillis() - start
                    );
                }
            });
    }

    /**
     * This method can be overridden to provide a hook before executing a scheduled service state update.
     *
     * @return {@code true} for continuing scheduled update.
     */
    protected boolean beforeScheduledStateUpdate(final Instant now,
                                                 final Service service,
                                                 final ServiceInstance instance) {
        return true; // noop
    }

    /**
     * Gets the Instant of the last time the state was successfully updated.
     *
     * @return the {@link Instant}.
     */
    protected Instant lastSucceedStateUpdated() {
        return lastSucceedStateUpdated;
    }

    /**
     * Updates a service with the given state.
     *
     * @param newState           the new state, or {@code null} to update using current state.
     * @param onStateChangeError the callback to invoke if the state cannot be changed.
     * @return the updated {@link ServiceInstance}, or {@code null} if state exist for the service.
     */
    protected ServiceInstance updateServiceInstanceState(final Instant now,
                                                         final Service service,
                                                         @Nullable Service.ServiceState newState,
                                                         final OnStateTransitionFailureCallback onStateChangeError) {

        LocalServiceState localServiceState = localServiceState(service);
        if (localServiceState == null) {
            return null; // service has been unregistered.
        }

        // If a new state is passed, then pre-check that transition
        // is valid with the known local service state.
        if (newState != null) {
            ServiceInstance localInstance = localServiceState.instance();
            if (!localInstance.state().isValidTransition(newState)) {
                log.warn("Failed to transition service [id={}, type={}, hostname={}] from {} to {}. Cause: {}.",
                    localInstance.uid(),
                    localInstance.type(),
                    localInstance.server().hostname(),
                    localInstance.state(),
                    newState,
                    "Invalid transition"
                );
                mayDisableStateUpdate(service, localInstance);
                return localInstance;
            }
        }

        // Ensure only one thread can update any instance at a time.
        stateLock.lock();
        // Optional callback to be executed at the end.
        Runnable returnCallback = null;
        try {
            localServiceState = localServiceState(service);

            if (localServiceState == null) {
                return null; // service has been unregistered.
            }

            if (newState == null) {
                newState = localServiceState.instance().state(); // use current state.
            }

            // Get an updated view of the local instance.
            final ServiceInstance localInstance = localServiceState.instance()
                .metrics(localServiceState.service().getMetrics())
                .server(serverInstanceFactory.newServerInstance());

            ServiceStateTransition.Response response = serviceLivenessUpdater.update(localInstance, newState);
            ServiceInstance remoteInstance = response.instance();

            boolean isStateTransitionSucceed = response.is(Result.SUCCEEDED);

            if (response.is(Result.ABORTED)) {
                // Force state transition due to inconsistent state; remote state does not exist (yet).
                remoteInstance = localInstance.state(newState, now);
                serviceLivenessUpdater.update(remoteInstance);
                isStateTransitionSucceed = true;
            }

            if (response.is(Result.FAILED)) {
                mayDisableStateUpdate(service, remoteInstance);

                // Register the OnStateTransitionFailureCallback
                final ServiceInstance instance = remoteInstance;
                returnCallback = () -> {
                    Optional<ServiceInstance> result = onStateChangeError.execute(now, service, instance, isLivenessEnabled());
                    if (result.isPresent()) {
                        // Optionally recover from state-transition failure
                        final ServiceInstance recovered = result.get();
                        serviceLivenessUpdater.update(recovered);
                        this.serviceRegistry.register(localServiceState(service).with(recovered));
                        this.lastSucceedStateUpdated = now;
                    }
                };
            }

            if (isStateTransitionSucceed) {
                this.lastSucceedStateUpdated = now;
            }
            // Update the local instance
            this.serviceRegistry.register(localServiceState.with(remoteInstance));
        } catch (Exception e) {
            final ServiceInstance localInstance = localServiceState(service).instance();
            log.error("[Service id={}, type='{}', hostname='{}'] Failed to update state to {}. Error: {}",
                localInstance.uid(),
                localInstance.type(),
                localInstance.server().hostname(),
                newState.name(),
                e.getMessage()
            );
        } finally {
            stateLock.unlock();
            // Because the callback may trigger a new thread that will update
            // the service instance we must ensure that we run it after calling unlock.
            if (returnCallback != null) {
                returnCallback.run();
            }
        }
        return localServiceState(service).instance();
    }

    private void mayDisableStateUpdate(final Service service, final ServiceInstance instance) {
        Service.ServiceState actualState = instance.state();
        if (actualState.hasCompletedTermination()) {
            log.error(
                "[Service id={}, type={}, hostname={}] Termination already completed ({}). " +
                    "This error may occur if the service has already been evicted by Kestra due to a prior error.",
                instance.uid(),
                instance.type(),
                instance.server().hostname(),
                actualState
            );
            // Mark the service has not updatable to prevent any unnecessary state transition issues.
            localServiceState(service).isStateUpdatable().set(false);
        }
    }

    private LocalServiceState localServiceState(final Service service) {
        return serviceRegistry.get(service.getType());
    }

    /**
     * Callback to be invoked when a state transition failed.
     */
    @FunctionalInterface
    public interface OnStateTransitionFailureCallback {

        OnStateTransitionFailureCallback NOOP = (now, service, instance, isLivenessEnabled) -> Optional.empty();

        /**
         * The callback method.
         *
         * @param service  the service.
         * @param instance the service instance.
         * @return an optional {@link ServiceInstance} that be used to force a state transition.
         */
        Optional<ServiceInstance> execute(Instant now,
                                          Service service,
                                          ServiceInstance instance,
                                          boolean isLivenessEnabled);
    }

    public static final class DefaultStateTransitionFailureCallback implements OnStateTransitionFailureCallback {

        /**
         * {@inheritDoc}
         **/
        @Override
        public Optional<ServiceInstance> execute(final Instant now,
                                                 final Service service,
                                                 final ServiceInstance instance,
                                                 final boolean isLivenessEnabled) {
            // Never shutdown STANDALONE server or WEB_SERVER service.
            if (instance.server().type().equals(ServerInstance.Type.STANDALONE) ||
                instance.is(Service.ServiceType.WEBSERVER)) {
                // Force the RUNNING state.
                return Optional.of(instance.state(Service.ServiceState.RUNNING, now, null));
            }

            if (isLivenessEnabled || instance.is(Service.ServiceState.ERROR)) {
                log.error("[Service id={}, type={}, hostname='{}'] Terminating server.",
                    instance.uid(),
                    instance.type(),
                    instance.server().hostname()
                );
                Service.ServiceState state = instance.state();
                // Skip graceful termination if the service was already considered being NOT_RUNNING by the coordinator.
                // More especially, this handles the case where a WORKER is configured with a short gracefulTerminationPeriod
                // and the JVM was unresponsive for more than this period.
                // In this context, the worker's tasks have already been resubmitted by the executor; the worker must therefore stop immediately.
                if (state.equals(Service.ServiceState.NOT_RUNNING) || state.equals(Service.ServiceState.EMPTY)) {
                    service.skipGracefulTermination(true);
                }
                KestraContext.getContext().shutdown();
                return Optional.empty();
            }

            // This should not happen, but let's log a WARN to keep a trace.
            log.warn("[Service id={}, type={}, hostname='{}'] Received unexpected state [{}] transition error [bug].",
                instance.uid(),
                instance.type(),
                instance.server().hostname(),
                instance.state()
            );
            return Optional.empty();
        }
    }

    @VisibleForTesting
    public List<ServiceInstance> allServiceInstances() {
        return serviceRegistry.all().stream().map(LocalServiceState::instance).toList();
    }

    @VisibleForTesting
    public void updateServiceInstance(final Service service, final ServiceInstance instance) {
        this.serviceRegistry.register(new LocalServiceState(service, instance));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    @PreDestroy
    public void close() {
        // Ensures that all service are closed before the ServiceLivenessManager.
        List<LocalServiceState> states = serviceRegistry.all();
        for (LocalServiceState state : states) {
            final Service service = state.service();
            try {
                service.unwrap().close();
            } catch (Exception e) {
                log.error("[Service id={}, type={}] Unexpected error on close",
                    service.getId(),
                    service.getType(),
                    e
                );
            }
        }
        super.close();
    }
}