package io.kestra.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@Builder
@EqualsAndHashCode
public class KestraMcpTransportContext implements McpTransportContext {
    private final String tenantId;
    private final String serverId;
    private final String userId;
    private String sessionId;

    @Override
    public Object get(String key) {
        return Map.of(
            "tenantId", tenantId,
            "serverId", serverId,
            "sessionId", sessionId,
            "userId", userId
        ).get(key);
    }
}
