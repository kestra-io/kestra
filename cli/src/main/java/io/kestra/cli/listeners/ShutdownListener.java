package io.kestra.cli.listeners;

import io.kestra.worker.DefaultWorker;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ShutdownEvent;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Global application shutdown handler.
 * This handler gets effectively invoked before {@link jakarta.annotation.PreDestroy} does.
 */
@Slf4j
public class ShutdownListener implements ApplicationEventListener<ShutdownEvent> {
    @Inject
    DefaultWorker worker;

    /**
     * {@inheritDoc}
     **/
    @Override
    public void onApplicationEvent(ShutdownEvent event) {
        log.debug("Shutdown event received");
        worker.close();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public boolean supports(ShutdownEvent event) {
        return ApplicationEventListener.super.supports(event);
    }
}
