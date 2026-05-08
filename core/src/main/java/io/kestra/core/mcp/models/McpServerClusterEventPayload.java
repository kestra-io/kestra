package io.kestra.core.mcp.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.kestra.core.serializers.JacksonMapper;

/**
 * JSON payload carried in the {@code message} field of {@link io.kestra.core.server.ClusterEvent}s
 * with type {@code MCP_SERVER_CHANGED}.
 * Using JSON allows the payload to evolve (new fields, optional properties) without breaking existing consumers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpServerClusterEventPayload(String tenantId, String serverId, boolean deleted, boolean disabled) {

    public boolean isDeletedOrDisabled() {
        return deleted || disabled;
    }

    public static McpServerClusterEventPayload of(McpServer mcpServer) {
        return new McpServerClusterEventPayload(mcpServer.tenantId(), mcpServer.id(), mcpServer.deleted(), mcpServer.disabled());
    }

    public String toJson() {
        try {
            return JacksonMapper.ofJson().writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize McpServerClusterEventPayload", e);
        }
    }

    public static McpServerClusterEventPayload fromJson(String json) {
        try {
            return JacksonMapper.ofJson().readValue(json, McpServerClusterEventPayload.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize McpServerClusterEventPayload", e);
        }
    }
}
