package io.kestra.webserver.services.ai.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.agent.domain.Message;
import io.kestra.webserver.services.ai.agent.domain.MessageRole;
import io.kestra.webserver.services.ai.agent.domain.MessageType;
import io.kestra.webserver.services.ai.agent.domain.ToolCall;
import io.kestra.webserver.services.ai.agent.store.MessageStore;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Append-only view over the {@link MessageStore} for a turn: builds and persists Message rows,
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

    public List<Message> load(final String threadId) {
        return messageStore.load(threadId);
    }

    public void appendUser(final String threadId, final String traceId, final String content) {
        append(threadId, traceId, MessageRole.USER, MessageType.TEXT, content, null, null);
    }

    public void appendAssistantText(final String threadId, final String traceId, final String content) {
        append(threadId, traceId, MessageRole.ASSISTANT, MessageType.TEXT, content, null, null);
    }

    public void appendToolCall(final String threadId, final String traceId, final String content, final ToolCall toolCall) {
        append(threadId, traceId, MessageRole.ASSISTANT, MessageType.TOOL_CALL, content, toolCall, null);
    }

    public void appendProposedAction(final String threadId, final String traceId, final String content, final ToolCall toolCall) {
        append(threadId, traceId, MessageRole.ASSISTANT, MessageType.PROPOSED_ACTION, content, toolCall, null);
    }

    public void appendToolResult(final String threadId, final String traceId, final ToolCall toolCall, final Map<String, Object> result) {
        append(threadId, traceId, MessageRole.TOOL, MessageType.TOOL_RESULT, null, toolCall, result);
    }

    public String deriveTitle(final String threadId) {
        return messageStore.load(threadId).stream()
            .filter(m -> m.role() == MessageRole.USER && m.type() == MessageType.TEXT && m.content() != null)
            .findFirst()
            .map(m -> m.content().length() > MAX_TITLE_LENGTH ? m.content().substring(0, MAX_TITLE_LENGTH) + "…" : m.content())
            .orElse(null);
    }

    private void append(final String threadId, final String traceId, final MessageRole role, final MessageType type,
                        final String content, final ToolCall toolCall, final Map<String, Object> toolResult) {
        messageStore.append(Message.builder()
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
