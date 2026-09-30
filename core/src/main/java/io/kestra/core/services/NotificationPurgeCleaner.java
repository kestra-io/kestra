package io.kestra.core.services;

import io.kestra.core.annotations.RequiresExecutor;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Enforces the flat retention TTL on {@code Notification}s (see {@link NotificationService#purge()}).
 */
@Slf4j
@Requires(property = "kestra.notifications.purge-expired.enabled", value = "true", defaultValue = "true")
@RequiresExecutor
@Singleton
public class NotificationPurgeCleaner {

    @Inject
    private NotificationService notificationService;

    @Scheduled(
        initialDelay = "${kestra.notifications.purge-expired.initial-delay:PT1H}",
        fixedDelay = "${kestra.notifications.purge-expired.fixed-delay:PT1H}"
    )
    public void purgeExpired() {
        int deleted = notificationService.purge();
        if (deleted > 0) {
            log.info("Purged {} expired notifications", deleted);
        }
    }
}
