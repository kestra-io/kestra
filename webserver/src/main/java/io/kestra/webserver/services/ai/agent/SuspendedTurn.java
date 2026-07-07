package io.kestra.webserver.services.ai.agent;

import java.util.List;

import io.kestra.core.utils.IdUtils;
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
    public static SuspendedTurn forPlan(final AgentLoopContext context) {
        return baseBuilder(context)
            .planProposal(true)
            .heldRequest(null)
            .build();
    }

    public static SuspendedTurn forAction(final AgentLoopContext context, final ToolExecutionRequest heldRequest) {
        return baseBuilder(context)
            .planProposal(false)
            .heldRequest(heldRequest)
            .build();
    }

    private static SuspendedTurnBuilder baseBuilder(final AgentLoopContext context) {
        return SuspendedTurn.builder()
            .confirmationId(IdUtils.create())
            .threadId(context.thread().uid())
            .tenant(context.tenant())
            .providerId(context.providerId())
            .mode(context.mode())
            .profile(context.profile())
            .messages(context.messages())
            .traceId(context.traceId());
    }
}
