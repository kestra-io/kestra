package io.kestra.webserver.services.ai.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.agent.domain.AgentMessage;
import io.kestra.webserver.services.ai.agent.domain.AgentMessageRole;
import io.kestra.webserver.services.ai.agent.domain.AgentMessageType;
import io.kestra.webserver.services.ai.agent.domain.AgentToolCall;
import io.kestra.webserver.services.ai.agent.store.MessageStore;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Append-only view over the {@link MessageStore} for a turn: builds and persists AgentMessage rows,
 * loads the log, and derives a thread title from the first user message.
 */
@Singleton
public class ConversationLog {
    private static final int MAX_TITLE_LENGTH = 60;

    private final MessageStore messageStore;
    private final AtomicLong sequence = new AtomicLong();

    @Inject
    public ConversationLog(final MessageStore messageStore) {
        this.messageStore = messageStore;
    }

    public List<AgentMessage> load(final String threadId) {
        return messageStore.load(threadId);
    }

    public void appendUser(final String threadId, final String traceId, final String content) {
        append(threadId, traceId, AgentMessageRole.USER, AgentMessageType.TEXT, content, null, null);
    }

    public void appendAssistantText(final String threadId, final String traceId, final String content) {
        append(threadId, traceId, AgentMessageRole.ASSISTANT, AgentMessageType.TEXT, content, null, null);
    }

    public void appendToolCall(final String threadId, final String traceId, final String content, final AgentToolCall toolCall) {
        append(threadId, traceId, AgentMessageRole.ASSISTANT, AgentMessageType.TOOL_CALL, content, toolCall, null);
    }

    public void appendProposedAction(final String threadId, final String traceId, final String content, final AgentToolCall toolCall) {
        append(threadId, traceId, AgentMessageRole.ASSISTANT, AgentMessageType.PROPOSED_ACTION, content, toolCall, null);
    }

    public void appendToolResult(final String threadId, final String traceId, final AgentToolCall toolCall, final Map<String, Object> result) {
        append(threadId, traceId, AgentMessageRole.TOOL, AgentMessageType.TOOL_RESULT, null, toolCall, result);
    }

    public String deriveTitle(final String threadId) {
        return messageStore.load(threadId).stream()
            .filter(m -> m.role() == AgentMessageRole.USER && m.type() == AgentMessageType.TEXT && m.content() != null)
            .findFirst()
            .map(m -> m.content().length() > MAX_TITLE_LENGTH ? m.content().substring(0, MAX_TITLE_LENGTH) + "…" : m.content())
            .orElse(null);
    }

    private void append(final String threadId, final String traceId, final AgentMessageRole role, final AgentMessageType type,
                        final String content, final AgentToolCall toolCall, final Map<String, Object> toolResult) {
        messageStore.append(AgentMessage.builder()
            .uid(newMessageUid())
            .threadId(threadId)
            .role(role)
            .type(type)
            .content(content)
            .toolCall(toolCall)
            .toolResult(toolResult)
            .traceId(traceId)
            .createdAt(Instant.now())
            .build());
    }

    private String newMessageUid() {
        return String.format("%019d-%s", sequence.incrementAndGet(), IdUtils.create());
    }
}
