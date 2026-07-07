package io.kestra.webserver.services.ai.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.agent.domain.AgentMessage;
import io.kestra.webserver.services.ai.agent.domain.AgentMessageRole;
import io.kestra.webserver.services.ai.agent.domain.AgentMessageType;
import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.domain.AgentThread;
import io.kestra.webserver.services.ai.agent.domain.AgentThreadStatus;
import io.kestra.webserver.services.ai.agent.domain.AgentToolCall;
import io.kestra.webserver.services.ai.agent.store.MessageStore;
import io.kestra.webserver.services.ai.agent.store.ThreadStore;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Owns a Copilot thread end to end: its lifecycle (status transitions in the {@link ThreadStore}) and
 * its append-only conversation log (building and persisting {@link AgentMessage} rows in the
 * {@link MessageStore}), including deriving the thread title from the first user message.
 */
@Singleton
public class AiThreadManager {
    private static final int MAX_TITLE_LENGTH = 60;

    private final ThreadStore threadStore;
    private final MessageStore messageStore;
    private final String nodeId = "node-" + IdUtils.create();
    private final AtomicLong sequence = new AtomicLong();

    @Inject
    public AiThreadManager(final ThreadStore threadStore, final MessageStore messageStore) {
        this.threadStore = threadStore;
        this.messageStore = messageStore;
    }

    // --- Thread lifecycle ---

    public Optional<AgentThread> find(final String tenant, final String uid) {
        return threadStore.find(tenant, uid);
    }


    public Optional<AgentThread> tryMarkRunning(final AgentThread thread, final AgentMode mode, final AgentThreadStatus expected) {
        return threadStore.updateIf(thread.tenant(), thread.uid(), expected, t -> t.toBuilder()
            .status(AgentThreadStatus.RUNNING)
            .ownerNodeId(nodeId)
            .mode(mode)
            .updatedAt(Instant.now())
            .build());
    }

    public AgentThread markAwaiting(final AgentThread thread) {
        return threadStore.save(thread.withStatus(AgentThreadStatus.AWAITING_CONFIRMATION).withUpdatedAt(Instant.now()));
    }

    public AgentThread finish(final AgentThread thread) {
        Instant now = Instant.now();
        AgentThread.AgentThreadBuilder builder = thread.toBuilder()
            .status(AgentThreadStatus.IDLE)
            .ownerNodeId(null)
            .lastTurnAt(now)
            .updatedAt(now);
        if (thread.title() == null) {
            builder.title(deriveTitle(thread.uid()));
        }
        return threadStore.save(builder.build());
    }

    public void resetToIdle(final AgentThread thread) {
        threadStore.find(thread.tenant(), thread.uid()).ifPresent(t ->
            threadStore.save(t.withStatus(AgentThreadStatus.IDLE).withOwnerNodeId(null).withUpdatedAt(Instant.now()))
        );
    }

    // --- Conversation log ---

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

    public void appendCancelled(final String threadId, final String traceId) {
        append(threadId, traceId, AgentMessageRole.SYSTEM, AgentMessageType.CANCELLED, null, null, null);
    }

    // --- Private helpers ---

    private String deriveTitle(final String threadId) {
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
