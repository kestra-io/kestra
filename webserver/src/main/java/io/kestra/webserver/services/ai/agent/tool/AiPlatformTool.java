package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

/**
 * A native platform tool — a deterministic platform call. This base declares no authorization; a
 * replacement can associate each tool with a permission that the {@link AgentToolPermissionEvaluator}
 * consumes at {@link ToolCatalog#dispatch}.
 */
public interface AiPlatformTool extends AiTool {
    AgentToolFamily family();

    AgentWritePolicy writePolicy();
}
