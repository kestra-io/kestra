package io.kestra.webserver.services.ai.agent;

import java.util.List;

import io.kestra.webserver.services.ai.agent.ModeProfiles.ResolvedProfile;
import io.kestra.webserver.services.ai.agent.domain.AgentMode;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import io.micronaut.core.annotation.Nullable;
import lombok.Builder;

@Builder
public record SuspendedTurn(
    String confirmationId,
    String threadId,
    String tenant,
    String providerId,
    AgentMode mode,
    ResolvedProfile profile,
    List<ChatMessage> messages,
    String traceId,
    boolean planProposal,
    @Nullable ToolExecutionRequest heldRequest
) {
}
