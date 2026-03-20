package io.kestra.core.server;

import io.kestra.core.utils.IdUtils;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class AbstractService implements Service {
    
    private final String id;
    private final ServiceType serviceType;
    private final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher;
    
    private final AtomicReference<ServiceState> state = new AtomicReference<>();
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    
    public AbstractService(ServiceType serviceType, ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher) {
        this.id = IdUtils.create();
        this.serviceType = serviceType;
        this.eventPublisher = eventPublisher;
    }
    
    protected void setState(final ServiceState state) {
        this.state.set(state);
        this.eventPublisher.publishEvent(new ServiceStateChangeEvent(this, getProperties()));
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

    /**
     * Marks this service as forcibly stopped without going through the normal shutdown lifecycle.
     * <p>
     * This is intended for abrupt, non-graceful stops (e.g., simulated crashes in tests).
     * It sets the {@code stopped} flag so that subsequent {@link #stop()} calls are no-ops,
     * and publishes a {@link ServiceStateChangeEvent} with {@link ServiceState#TERMINATED_FORCED}
     * so that liveness monitors stop heartbeating this service immediately.
     * </p>
     */
    protected void markAsForciblyStopped() {
        if (stopped.compareAndSet(false, true)) {
            setState(ServiceState.TERMINATED_FORCED);
        }
    }
}
