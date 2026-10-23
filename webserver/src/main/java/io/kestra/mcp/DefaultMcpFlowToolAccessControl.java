package io.kestra.mcp;

import io.kestra.core.models.AccessScope;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * Access control for editions without namespace permissions, where every caller may execute every flow a
 * server exposes.
 */
@Singleton
public class DefaultMcpFlowToolAccessControl implements McpFlowToolAccessControl {
    @Override
    public AccessScope executableScope(@Nullable String callerId, String tenantId, String serverId) {
        return AccessScope.global();
    }
}
