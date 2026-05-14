package io.kestra.core.server;

import java.time.LocalDateTime;

import io.kestra.core.models.HasUID;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.utils.IdUtils;

public record ClusterEvent(String uid, EventType eventType, LocalDateTime eventDate, String message) implements HasUID, BroadcastEvent {

    public ClusterEvent(EventType eventType, LocalDateTime eventDate, String message) {
        this(IdUtils.create(), eventType, eventDate, message);
    }

    @Override
    public String key() {
        return uid;
    }

    public enum EventType {
        MAINTENANCE_ENTER,
        MAINTENANCE_EXIT,
        PLUGINS_SYNC_REQUESTED,
        KILL_SWITCH_SYNC_REQUESTED,
        MCP_SERVER_CHANGED
    }
}
