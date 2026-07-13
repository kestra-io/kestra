package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

public interface AiPlatformTool {
    AgentToolFamily family();

    AgentWritePolicy writePolicy();

    String permission();

    default Object toolInstance() {
        return this;
    }
}
