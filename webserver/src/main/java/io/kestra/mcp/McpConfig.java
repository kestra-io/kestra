package io.kestra.mcp;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("kestra.mcp")
public record McpConfig(
    ToolCacheConfig toolCacheConfig,
    ServerCacheConfig serverCacheConfig,
    @Bindable(defaultValue = "PT5M") Duration toolExecutionTimeout) {

    /**
     * @param maximumSize maximum number of entries in the tool-list cache (default: 250)
     * @param expireAfterAccess how long an entry stays cached after last access (default: 5 minutes)
     */
    @ConfigurationProperties("toolCacheConfig")
    public record ToolCacheConfig(
        @Bindable(defaultValue = "250") Long maximumSize,
        @Bindable(defaultValue = "PT5M") Duration expireAfterAccess) {
    }

    /**
     * @param maximumSize maximum number of MCP server entries cached per webserver node (default: 500)
     * @param expireAfterAccess how long an entry stays cached after last access (default: 5 minutes)
     */
    @ConfigurationProperties("serverCacheConfig")
    public record ServerCacheConfig(
        @Bindable(defaultValue = "500") Long maximumSize,
        @Bindable(defaultValue = "PT5M") Duration expireAfterAccess) {
    }
}
