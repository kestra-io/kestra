package io.kestra.mcp;

import java.util.List;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * The platform MCP server, which exposes Kestra's own tools across tenants at {@code /api/v1/mcp}.
 *
 * @param enabled whether the server is served at all (default: false)
 * @param tenants the tenants its tools may act on; empty means every tenant the caller can access
 * @param instructions instructions sent to MCP clients when they connect
 */
@ConfigurationProperties("kestra.mcp.platform-server")
public record PlatformMcpServerConfig(
    @Bindable(defaultValue = "false") boolean enabled,
    @Nullable List<String> tenants,
    @Nullable String instructions) {
}
