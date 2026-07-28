package io.kestra.core.ai.agent.models;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.kestra.core.models.HasUID;

import io.micronaut.core.annotation.Nullable;
import lombok.Builder;

/**
 * One append-only step of a Copilot turn: one row in the {@code ai_agent_message} table, linked to its
 * parent thread by {@link #threadId()} and scoped by {@link #tenant()} (mirroring the owning thread) so
 * history can be loaded with tenant isolation. Rows are only ever inserted; a thread's history is loaded
 * ordered by its monotonic {@link #uid()}, so messages always load in the order they were appended
 * (e.g. a tool call always loads before its result).
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentMessage(
    String uid,
    @Nullable String tenant,
    String threadId,
    AgentMessageRole role,
    AgentMessageType type,
    @Nullable String content,
    @Nullable AgentToolCall toolCall,
    @Nullable Map<String, Object> toolResult,
    @Nullable ArtefactDraft draft,
    String traceId,
    Instant createdAt) implements HasUID {

    /** {@inheritDoc} */
    @Override
    public String uid() {
        return uid;
    }
}
