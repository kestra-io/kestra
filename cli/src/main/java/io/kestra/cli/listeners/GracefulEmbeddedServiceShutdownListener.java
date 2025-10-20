package io.kestra.cli.listeners;

import io.kestra.core.server.LocalServiceState;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

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
     * Wait for services' close actions
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

        List<CompletableFuture<Void>> futures = states.stream()
            .map(state -> CompletableFuture.runAsync(() -> closeService(state), ForkJoinPool.commonPool()))
            .toList();

        // Wait for all services to close, before shutting down the embedded server
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
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
