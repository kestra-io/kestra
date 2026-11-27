package io.kestra.mcp;

import io.kestra.core.models.AccessScope;

import io.micronaut.core.annotation.Nullable;

/**
 * Decides which flows a flow-backed MCP server exposes to a caller as tools, and which of them that caller may
 * invoke.
 */
public interface McpFlowToolAccessControl {
    /**
     * @param callerId the caller, or {@code null} when anonymous
     * @param tenantId the tenant the MCP server belongs to
     * @param serverId the MCP server being connected to
     * @return the namespaces whose flows this caller may execute through that server
     */
    AccessScope executableScope(@Nullable String callerId, String tenantId, String serverId);
}
