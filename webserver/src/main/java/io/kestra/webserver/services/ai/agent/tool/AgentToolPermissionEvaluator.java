package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.webserver.services.ai.agent.domain.AgentPrincipal;

import io.micronaut.core.annotation.Nullable;

/**
 * The coarse, dispatch-time authorization gate — the tool-catalog analog of the
 * {@code @HasAnyResource} security rule on EE controllers: "may this caller use this tool in this
 * tenant at all". OSS allows everything; EE checks the caller's grants against the tool's declared
 * {@code RbacPermission}. Per-namespace authorization is not done here — it lives in the EE tool
 * subclasses (like the in-body {@code isNamespaceAllowedOrThrow} calls in EE controllers).
 */
public interface AgentToolPermissionEvaluator {
    boolean isAllowed(ToolCatalog.ToolEntry entry, String tenant, @Nullable AgentPrincipal principal);
}
