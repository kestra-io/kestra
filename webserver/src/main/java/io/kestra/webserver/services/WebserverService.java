package io.kestra.webserver.services;

import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.event.ServerShutdownEvent;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class is used for service registration and liveness.
 */
@Context
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
public final class WebserverService implements Service {

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final String id = IdUtils.create();

    @Inject
    private ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher;

    private final AtomicReference<ServiceState> state = new AtomicReference<>();

    private void setState(final ServiceState state) {
        this.state.set(state);
        eventPublisher.publishEvent(new ServiceStateChangeEvent(this));
    }

    /** {@inheritDoc} **/
    @Override
    public String getId() {
        return id;
    }

    /** {@inheritDoc} **/
    @Override
    public ServiceType getType() {
        return ServiceType.WEBSERVER;
    }

    /** {@inheritDoc} **/
    @Override
    public ServiceState getState() {
        return state.get();
    }

    @PostConstruct
    public void postConstruct() {
        setState(ServiceState.CREATED);
    }

    @EventListener
    public void onServerStartup(ServerStartupEvent event) {
        setState(ServiceState.RUNNING);
    }

    /** {@inheritDoc} **/
    @Override
    public void close() {
        if (this.shutdown.compareAndSet(false, true)) {
            setState(ServiceState.TERMINATING);
            setState(ServiceState.TERMINATED_GRACEFULLY);
        }
    }

    @EventListener
    public void onServeShutdown(ServerShutdownEvent event) {
        close();
    }
}
