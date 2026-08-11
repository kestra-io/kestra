package io.kestra.core.server;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.kestra.core.utils.IdUtils;

import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AbstractService implements Service {

    /**
     * How long {@link #stop()} waits for an in-flight startup to complete before proceeding with a
     * best-effort teardown. Generous on purpose: a worker startup can legitimately block up to the
     * gRPC connect deadline (30s by default) before creating its resources.
     */
    private static final Duration STARTUP_COMPLETION_TIMEOUT = Duration.ofMinutes(1);

    private final String id;
    private final ServiceType serviceType;
    private final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher;

    private final AtomicReference<ServiceState> state = new AtomicReference<>();
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final ServiceLifecycleGuard lifecycleGuard = new ServiceLifecycleGuard();

    public AbstractService(ServiceType serviceType, ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher) {
        this.id = IdUtils.create();
        this.serviceType = serviceType;
        this.eventPublisher = eventPublisher;
    }

    protected void setState(final ServiceState state) {
        // Once a stop began, drop startup-side transitions (CREATED/RUNNING/MAINTENANCE): a startup
        // or maintenance-listener thread racing the stop could otherwise overwrite the terminal
        // state, leaving a fully torn-down service published as RUNNING. updateAndGet re-evaluates
        // on contention, so a concurrent stop's terminal write can never be clobbered.
        ServiceState result = this.state.updateAndGet(current -> stopped.get() && state.isRunning() ? current : state);
        if (result == state) {
            this.eventPublisher.publishEvent(new ServiceStateChangeEvent(this, getProperties()));
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ServiceType getType() {
        return serviceType;
    }

    @Override
    public ServiceState getState() {
        return state.get();
    }

    protected Map<String, Object> getProperties() {
        return Map.of();
    }

    /**
     * Runs the given startup body under the service lifecycle guard. Services started
     * asynchronously (runner thread pool) MUST run their startup through this template:
     * <ul>
     * <li>when {@link #stop()} was already requested (e.g. the application context is closing),
     * the startup is aborted and nothing is created;</li>
     * <li>while the body runs, a concurrent {@link #stop()} waits (bounded) for it to complete, so
     * the teardown can never miss a resource created by the startup;</li>
     * <li>when the body completes and no stop was requested meanwhile, {@code onStarted} runs — it
     * typically transitions the service to RUNNING (or MAINTENANCE) and logs the successful start;</li>
     * <li>a pending stop is always unblocked, even when the body throws.</li>
     * </ul>
     *
     * @param startup the startup body, creating the service resources.
     * @param onStarted the effective-start hook, skipped when a stop is pending.
     * @throws IllegalStateException if the service was already started.
     */
    protected final void guardedStart(final Runnable startup, final Runnable onStarted) {
        final String serviceName = getClass().getSimpleName();
        if (!lifecycleGuard.beginStart()) {
            log.info("Service [{}] stop was requested before startup began, aborting startup", serviceName);
            return;
        }
        try {
            startup.run();
            if (lifecycleGuard.endStart()) {
                onStarted.run();
            } else {
                log.info("Service [{}] startup completed with a stop pending, terminating", serviceName);
            }
        } catch (RejectedExecutionException e) {
            // A stop() gave up waiting for this startup (startup-completion timeout) and already
            // tore the service's thread pools down; the late startup then submitting to them is the
            // expected outcome of that best-effort teardown, not a fatal error, so we didn't let it propagate.
            log.warn("Service [{}] startup aborted: a stop tore down its resources while the startup was still in flight", serviceName, e);
        } finally {
            // unblock a pending stop even when the startup fails
            lifecycleGuard.endStart();
        }
    }

    /**
     * @return {@code true} once {@link #stop()} has been requested. Long startups can use it as a
     *         cheap cooperative checkpoint to abort early.
     */
    protected boolean isStopRequested() {
        return lifecycleGuard.isStopRequested();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        stop();
    }

    @PreDestroy
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            setState(ServiceState.TERMINATING);
            log.info("Service [{}] is terminating", getClass().getSimpleName());
            // Refuse any future startup and wait (bounded) for an in-flight one, so doStop() never
            // races a half-done startup: it would miss the resources created right after and leave
            // them running once the application context (and e.g. its datasource) is closed.
            lifecycleGuard.beginStop(STARTUP_COMPLETION_TIMEOUT);
            try {
                ServiceState serviceState = doStop();
                setState(serviceState);
            } catch (Exception e) {
                log.debug("Error while stopping service [{}]", this.getClass().getSimpleName(), e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                setState(ServiceState.TERMINATED_FORCED);
            }
            log.info("Service [{}] stopped {}", this.getClass().getSimpleName(), getState());
        }
    }

    protected ServiceState doStop() throws Exception {
        return ServiceState.TERMINATED_GRACEFULLY;
    }
}
