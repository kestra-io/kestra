package io.kestra.mcp;

import java.util.Optional;

import io.kestra.core.mcp.models.McpServer;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Defines the platform MCP server from {@link PlatformMcpServerConfig}. It belongs to no tenant, so its fixed
 * {@link #SERVER_ID} can never collide with a stored server, and it is always private.
 */
@Singleton
@Requires(property = "kestra.mcp.platform-server.enabled", value = "true")
public class PlatformMcpServerProvider implements McpServerProvider {
    public static final String SERVER_ID = "platform";

    private final PlatformMcpServerConfig config;

    @Inject
    public PlatformMcpServerProvider(PlatformMcpServerConfig config) {
        this.config = config;
    }

    @Override
    public McpToolSource.Kind toolSource() {
        return McpToolSource.Kind.PLATFORM;
    }

    @Override
    public Optional<McpServer> get(@Nullable String tenantId, String serverId) {
        if (tenantId != null || !SERVER_ID.equals(serverId)) {
            return Optional.empty();
        }
        return Optional.of(server(McpServer.AuthType.BASIC, null));
    }

    protected McpServer server(McpServer.AuthType authType, @Nullable String oauthProvider) {
        return new McpServer(
            null, SERVER_ID, "Kestra platform tools", config.instructions(),
            McpServer.ServerType.PRIVATE, authType, oauthProvider, null, false, false, false, null, null
        );
    }
}
