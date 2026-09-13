package io.kestra.webserver.services.ai.agent.data;

import java.time.Instant;
import java.util.Map;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.models.AgentMessageRole;
import io.kestra.core.ai.agent.models.AgentMessageType;
import io.kestra.core.ai.agent.models.AgentToolCall;
import io.kestra.core.ai.agent.models.ArtefactDraft;

import io.micronaut.core.annotation.Nullable;

public record ApiMessageView(
    String uid,
    AgentMessageRole role,
    AgentMessageType type,
    @Nullable String content,
    @Nullable AgentToolCall toolCall,
    @Nullable Map<String, Object> toolResult,
    @Nullable ArtefactDraft draft,
    Instant createdAt) {
    public static ApiMessageView from(final AgentMessage message) {
        return new ApiMessageView(
            message.uid(), message.role(), message.type(), message.content(),
            message.toolCall(), message.toolResult(), message.draft(), message.createdAt()
        );
    }
}
