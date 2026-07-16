package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.core.ai.agent.models.AgentPrincipal;

import io.micronaut.core.annotation.Nullable;

/**
 * The coarse, dispatch-time authorization gate: "may this caller use this tool in this tenant at
 * all". The default implementation allows everything; a replacement can check the caller against the
 * tool's declared permission. Finer-grained (e.g. per-namespace) authorization is not done here — it
 * belongs to the individual tool implementations.
 */
public interface AgentToolPermissionEvaluator {
    boolean isAllowed(ToolCatalog.ToolEntry entry, String tenant, @Nullable AgentPrincipal principal);
}
