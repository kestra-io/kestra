package io.kestra.core.mcp.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kestra.core.models.HasUID;
import io.kestra.core.queues.event.BroadcastEvent;
import io.kestra.core.utils.IdUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpSession(
    String tenantId,
    String serverId,
    String sessionId,
    String sseNode,
    String userId,
    boolean deleted
) implements HasUID, BroadcastEvent {

    /** {@inheritDoc} */
    @Override
    @JsonIgnore
    public String uid() {
        return IdUtils.fromParts(tenantId, sessionId);
    }

    /** {@inheritDoc} */
    @Override
    @JsonIgnore
    public String key() {
        return uid();
    }

    public McpSession toDeleted() {
        return new McpSession(tenantId, serverId, sessionId, sseNode, userId, true);
    }
}
