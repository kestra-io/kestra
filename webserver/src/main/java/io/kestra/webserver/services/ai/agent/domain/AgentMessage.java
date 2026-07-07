package io.kestra.webserver.services.ai.agent.domain;

import java.time.Instant;
import java.util.Map;

import io.micronaut.core.annotation.Nullable;
import lombok.Builder;

@Builder
public record AgentMessage(
    String uid,
    String threadId,
    AgentMessageRole role,
    AgentMessageType type,
    @Nullable String content,
    @Nullable AgentToolCall toolCall,
    @Nullable Map<String, Object> toolResult,
    String traceId,
    Instant createdAt
) {
}
