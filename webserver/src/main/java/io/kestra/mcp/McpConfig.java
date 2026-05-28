package io.kestra.mcp;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

import java.time.Duration;

@ConfigurationProperties("kestra.mcp")
public record McpConfig(
    ToolCacheConfig toolCacheConfig,
    ServerCacheConfig serverCacheConfig,
    @Bindable(defaultValue = "PT5M") Duration toolExecutionTimeout) {

    /**
     * @param maximumSize maximum number of entries in the tool-list cache (default: 250)
     * @param expireAfterAccess how long an entry stays cached after last access (default: 5 minutes)
     */
    @ConfigurationProperties("tool-cache-config")
    public record ToolCacheConfig(
        @Bindable(defaultValue = "250") Long maximumSize,
        @Bindable(defaultValue = "PT5M") Duration expireAfterAccess) {
    }

    /**
     * @param maximumSize maximum number of MCP server entries cached per webserver node (default: 500)
     * @param expireAfterAccess how long an entry stays cached after last access (default: 5 minutes)
     */
    @ConfigurationProperties("server-cache-config")
    public record ServerCacheConfig(
        @Bindable(defaultValue = "500") Long maximumSize,
        @Bindable(defaultValue = "PT5M") Duration expireAfterAccess) {
    }
}
