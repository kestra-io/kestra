package io.kestra.core.models.notifications;

import java.time.Instant;

import io.kestra.core.models.HasUID;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.utils.IdUtils;

import jakarta.annotation.Nullable;

public record NotificationEvent(String uid, NotificationEventType eventType, @Nullable String notificationId, @Nullable String referenceId, @Nullable String tenantId,
    Instant timestamp) implements HasUID, BroadcastEvent {
    @Override
    public String key() {
        return uid;
    }

    public static NotificationEvent of(NotificationEventType eventType, @Nullable String notificationId, String referenceId, @Nullable String tenantId) {
        return new NotificationEvent(IdUtils.create(), eventType, notificationId, referenceId, tenantId, Instant.now());
    }
}
