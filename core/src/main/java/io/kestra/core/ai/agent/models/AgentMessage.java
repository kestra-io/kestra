package io.kestra.core.ai.agent.models;

import java.time.Instant;
import java.util.Map;

import io.kestra.core.models.HasUID;

import io.micronaut.core.annotation.Nullable;
import lombok.Builder;

/**
 * A single message in a Copilot conversation thread. Persisted append-only with {@code uid} as its
 * cluster-wide key ({@link HasUID}); the {@code uid} is monotonically ordered so a thread's history
 * sorts chronologically by key. Keyed to its thread by {@code threadId}; not tenant-scoped directly.
 */
@Builder
public record AgentMessage(
    String uid,
    String threadId,
    AgentMessageRole role,
    AgentMessageType type,
    @Nullable String content,
    @Nullable AgentToolCall toolCall,
    @Nullable Map<String, Object> toolResult,
    @Nullable ArtefactDraft draft,
    String traceId,
    Instant createdAt) implements HasUID {
}
