package io.kestra.core.mcp.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.utils.IdUtils;

/**
 * Broadcast event wrapping an {@link McpSession} with an explicit type so consumers
 * do not need to infer intent from session properties.
 *
 * @param session the session this event refers to
 * @param type    the kind of change that occurred
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpSessionEvent(McpSession session, McpSessionEventType type) implements BroadcastEvent {

    public String uid() {
        return IdUtils.fromParts(session.tenantId(), session.sessionId());
    }

    @Override
    public String key() {
        return uid();
    }

    public enum McpSessionEventType {
        CREATED,
        OWNERSHIP_CHANGED,
        DELETED
    }
}
