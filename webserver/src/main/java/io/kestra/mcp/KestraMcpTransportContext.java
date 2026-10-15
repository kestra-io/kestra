package io.kestra.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * The caller is deliberately left out of {@code equals}/{@code hashCode}: a session is identified by its
 * tenant, server and session id, and is looked up from paths that know nothing about who is calling.
 */
@Data
@Builder
@EqualsAndHashCode(exclude = "userId")
public class KestraMcpTransportContext implements McpTransportContext {
    private final String tenantId;
    private final String serverId;
    private final String userId;
    private String sessionId;

    @Override
    public Object get(String key) {
        return switch (key) {
            case "tenantId" -> tenantId;
            case "serverId" -> serverId;
            case "sessionId" -> sessionId;
            case "userId" -> userId;
            default -> null;
        };
    }
}
