package io.kestra.core.async;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import io.kestra.core.notification.NotificationItemRepositoryInterface;
import io.kestra.core.notification.model.NotificationItem;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceResourceReleaser;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Fails the async operation items a dead service was running in-process, which nothing else would
 * ever complete. The failure goes through the async operation queue, like any other outcome, so
 * followers hear it and the aggregator records it.
 */
@Slf4j
@Singleton
public class AsyncOperationResourceReleaser implements ServiceResourceReleaser {
    private final NotificationItemRepositoryInterface notificationItemRepository;
    private final AsyncOperationService asyncOperationService;

    @Inject
    public AsyncOperationResourceReleaser(NotificationItemRepositoryInterface notificationItemRepository, AsyncOperationService asyncOperationService) {
        this.notificationItemRepository = Objects.requireNonNull(notificationItemRepository, "notificationItemRepository must not be null");
        this.asyncOperationService = Objects.requireNonNull(asyncOperationService, "asyncOperationService must not be null");
    }

    @Override
    public void releaseResources(ServiceInstance serviceInstance, String reason) {
        List<NotificationItem> pending = notificationItemRepository.findPendingByOwner(serviceInstance.uid());
        if (pending.isEmpty()) {
            return;
        }

        String error = "The %s service '%s' running this operation stopped before finishing it: %s".formatted(serviceInstance.type(), serviceInstance.uid(), reason);
        Instant now = Instant.now();
        pending.forEach(
            item -> asyncOperationService.emit(
                new AsyncOperationProcessedEvent(item.getOperationId(), item.getTenantId(), item.getResourceId(), AsyncOperationProcessedEvent.Outcome.FAILED, error, now)
            )
        );
        log.info("Failed {} async operation item(s) left pending by service instance '{}'. Reason: {}", pending.size(), serviceInstance.uid(), reason);
    }
}
