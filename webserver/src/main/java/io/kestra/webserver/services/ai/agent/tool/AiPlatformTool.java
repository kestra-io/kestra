package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

/**
 * A native platform tool — a deterministic platform call. OSS declares no permissions — EE extends
 * each tool (as it does for controllers), applying its RBAC mapping through the EE-side permission
 * interface, which the EE permission evaluator consumes at {@link ToolCatalog#dispatch}.
 */
public interface AiPlatformTool extends AiTool {
    AgentToolFamily family();

    AgentWritePolicy writePolicy();
}
