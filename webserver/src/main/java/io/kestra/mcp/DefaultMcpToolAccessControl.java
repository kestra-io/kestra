package io.kestra.mcp;

import java.security.Principal;
import java.util.Optional;

import io.kestra.core.models.AccessScope;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;

/**
 * Access control for editions without namespace permissions, where every caller may execute every flow a
 * server exposes.
 */
@Singleton
public class DefaultMcpToolAccessControl implements McpToolAccessControl {
    @Override
    public Optional<String> callerId(HttpRequest<?> request) {
        return request.getUserPrincipal().map(Principal::getName);
    }

    @Override
    public AccessScope executableScope(@Nullable String callerId, String tenantId, String serverId) {
        return AccessScope.global();
    }
}
