package io.kestra.mcp;

import java.util.UUID;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

@Singleton
public class McpSessionFactory {
    public KestraMcpTransportContext build(String tenantId, String serverId, @Nullable String sessionId) {
        return KestraMcpTransportContext.builder()
            .tenantId(tenantId)
            .serverId(serverId)
            .sessionId(sessionId != null ? sessionId : UUID.randomUUID().toString())
            .userId(null)
            .build();
    }
}
