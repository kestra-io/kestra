package io.kestra.core.listeners;

import io.micronaut.retry.event.RetryEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Singleton;

@Singleton
@Slf4j
public class RetryEvents {
    @EventListener
    void onRetry(final RetryEvent event) {
        log.info(
            "Retry from '{}.{}()', attempt {}, overallDelay {}",
            event.getSource().getTarget().getClass().getName(),
            event.getSource().getExecutableMethod().getName(),
            event.getRetryState().currentAttempt(),
            event.getRetryState().getOverallDelay()
        );
    }
}

