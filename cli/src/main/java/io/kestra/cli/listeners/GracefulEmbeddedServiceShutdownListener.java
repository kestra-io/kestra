package io.kestra.cli.listeners;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

import io.kestra.core.server.LocalServiceState;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceRegistry;
import io.kestra.core.server.ServiceType;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Global application shutdown handler.
 * This handler gets effectively invoked before {@link jakarta.annotation.PreDestroy} does.
 */
@Singleton
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@Requires(property = "kestra.server-type")
public class GracefulEmbeddedServiceShutdownListener implements ApplicationEventListener<ShutdownEvent> {
    @Inject
    ServiceRegistry serviceRegistry;

    /**
     * {@inheritDoc}
     **/
    @Override
    public boolean supports(ShutdownEvent event) {
        return ApplicationEventListener.super.supports(event);
    }

    /**
     * Wait for services' close actions.
     * The Controller is closed last to ensure workers can gracefully disconnect
     * before the gRPC server shuts down.
     *
     * @param event the event to respond to
     */
    @Override
    public void onApplicationEvent(ShutdownEvent event) {
        List<LocalServiceState> states = serviceRegistry.all();
        if (states.isEmpty()) {
            return;
        }

        log.debug("Shutdown event received");

        // Close all services except the Controller first
        List<CompletableFuture<Void>> futures = states.stream()
            .filter(state -> state.service().getType() != ServiceType.CONTROLLER)
            .map(state -> CompletableFuture.runAsync(() -> closeService(state), ForkJoinPool.commonPool()))
            .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Then close the Controller
        states.stream()
            .filter(state -> state.service().getType() == ServiceType.CONTROLLER)
            .forEach(this::closeService);
    }

    private void closeService(LocalServiceState state) {
        final Service service = state.service();
        try {
            service.unwrap().close();
        } catch (Exception e) {
            log.error("[Service id={}, type={}] Unexpected error on close", service.getId(), service.getType(), e);
        }
    }
}
