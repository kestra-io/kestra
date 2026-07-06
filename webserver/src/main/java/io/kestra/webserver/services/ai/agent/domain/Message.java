package io.kestra.webserver.services.ai.agent.domain;

import java.time.Instant;
import java.util.Map;

import io.micronaut.core.annotation.Nullable;
import lombok.Builder;

@Builder
public record Message(
    String uid,
    String threadId,
    MessageRole role,
    MessageType type,
    @Nullable String content,
    @Nullable ToolCall toolCall,
    @Nullable Map<String, Object> toolResult,
    String traceId,
    Instant createdAt
) {
}
