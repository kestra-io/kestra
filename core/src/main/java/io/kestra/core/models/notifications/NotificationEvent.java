package io.kestra.core.models.notifications;

import java.time.Instant;

import io.kestra.core.models.HasUID;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.utils.IdUtils;

public record NotificationEvent(String uid, NotificationEventType eventType, Notification notification, Instant timestamp) implements HasUID, BroadcastEvent {
    @Override
    public String key() {
        return uid;
    }

    public static NotificationEvent of(NotificationEventType eventType, Notification notification) {
        return new NotificationEvent(IdUtils.create(), eventType, notification, Instant.now());
    }
}
