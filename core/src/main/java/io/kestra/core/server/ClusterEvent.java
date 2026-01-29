package io.kestra.core.server;

import io.kestra.core.models.HasUID;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.utils.IdUtils;

import java.time.LocalDateTime;

public record ClusterEvent(String uid, EventType eventType, LocalDateTime eventDate, String message) implements HasUID, BroadcastEvent {

    public ClusterEvent(EventType eventType, LocalDateTime eventDate, String message) {
        this(IdUtils.create(), eventType, eventDate, message);
    }

    @Override
    public String key() {
        return uid;
    }

    public enum EventType { MAINTENANCE_ENTER, MAINTENANCE_EXIT, PLUGINS_SYNC_REQUESTED }
}
