package io.kestra.webserver.services.ai.agent.tool;

import io.kestra.webserver.services.ai.agent.domain.ToolFamily;
import io.kestra.webserver.services.ai.agent.domain.WritePolicy;

public interface AiPlatformTool {
    ToolFamily family();

    WritePolicy writePolicy();

    String permission();

    default Object toolInstance() {
        return this;
    }
}
