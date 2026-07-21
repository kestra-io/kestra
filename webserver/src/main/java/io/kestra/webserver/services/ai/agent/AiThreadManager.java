package io.kestra.webserver.services.ai.agent;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentMessageRole;
import io.kestra.core.ai.agent.models.AgentMessageType;
import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.ai.agent.models.AgentThreadStatus;
import io.kestra.core.ai.agent.models.AgentToolCall;
import io.kestra.core.ai.agent.models.ArtefactDraft;
import io.kestra.core.ai.agent.repositories.AiMessageRepositoryInterface;
import io.kestra.core.ai.agent.repositories.AiThreadRepositoryInterface;
import io.kestra.core.server.ServerInstance;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Owns a Copilot thread end to end: its lifecycle (status transitions in the
 * {@link AiThreadRepositoryInterface}) and its append-only conversation log (building and persisting
 * {@link AgentMessage} rows in the {@link AiMessageRepositoryInterface}), including deriving the thread
 * title from the first user message.
 */
@Singleton
public class AiThreadManager {
    private static final int MAX_TITLE_LENGTH = 60;

    private final AiThreadRepositoryInterface threadStore;
    private final AiMessageRepositoryInterface messageStore;
    private final AtomicLong sequence = new AtomicLong();

    @Inject
    public AiThreadManager(final AiThreadRepositoryInterface threadStore, final AiMessageRepositoryInterface messageStore) {
        this.threadStore = threadStore;
        this.messageStore = messageStore;
    }

    public Optional<AgentThread> find(final String tenant, final String uid) {
        return threadStore.find(tenant, uid);
    }

    /**
     * Lists a user's non-deleted threads, most-recently-active first (by last turn, falling back to the
     * last update). Backs the thread-management (listing) surface; the in-memory store returns nothing
     * here, so a listing is only non-empty when a durable repository is in use.
     */
    public List<AgentThread> list(final String tenant, final String userId) {
        return threadStore.findAllForUser(tenant, userId).stream()
            .sorted(Comparator.comparing(AiThreadManager::lastActivity).reversed())
            .toList();
    }

    /** Renames a thread, refreshing its update timestamp. Other fields (status, mode) are preserved. */
    public AgentThread rename(final AgentThread thread, final String title) {
        return threadStore.save(thread.withTitle(title).withUpdatedAt(Instant.now()));
    }

    /** Soft-deletes a thread; its history is retained until purge. */
    public AgentThread delete(final AgentThread thread) {
        return threadStore.delete(thread);
    }

    private static Instant lastActivity(final AgentThread thread) {
        return thread.lastTurnAt() != null ? thread.lastTurnAt() : thread.updatedAt();
    }

    public Optional<AgentThread> tryMarkRunning(final AgentThread thread, final AgentMode mode, final AgentThreadStatus expected) {
        return threadStore.updateIf(
            thread.tenant(), thread.uid(), expected, t -> t.toBuilder()
                .status(AgentThreadStatus.RUNNING)
                .ownerNodeId(ServerInstance.INSTANCE_ID)
                .mode(mode)
                .updatedAt(Instant.now())
                .build()
        );
    }

    /**
     * Marks a thread awaiting confirmation and durably records the confirmation token the client must
     * present to resume. The held action / plan itself is reconstructed from the message log, so
     * nothing else is captured.
     */
    public AgentThread markAwaiting(final AgentThread thread, final String confirmationId) {
        return threadStore.save(
            thread.withStatus(AgentThreadStatus.AWAITING_CONFIRMATION)
                .withPendingConfirmationId(confirmationId)
                .withUpdatedAt(Instant.now())
        );
    }

    public AgentThread finish(final AgentThread thread) {
        String title = thread.title() != null ? thread.title() : deriveTitle(thread.tenant(), thread.uid());
        AgentThread idle = thread.toIdle();
        return threadStore.save(idle.withLastTurnAt(idle.updatedAt()).withTitle(title));
    }

    public void resetToIdleIfExists(final String tenant, final String uid) {
        threadStore.find(tenant, uid).ifPresent(t -> threadStore.save(t.toIdle()));
    }

    public List<AgentMessage> load(final String tenant, final String threadId) {
        return messageStore.load(tenant, threadId);
    }

    /**
     * The number of user turns a thread already holds — its distinct turn traces. A confirmation resume
     * reuses the suspended turn's trace, so a suspend/resume counts as one turn. Backs the
     * {@code maxTurnsPerThread} guardrail.
     */
    public long turnCount(final String tenant, final String threadId) {
        return messageStore.load(tenant, threadId).stream()
            .map(AgentMessage::traceId)
            .distinct()
            .count();
    }

    public void appendUser(final String tenant, final String threadId, final String traceId, final String content) {
        append(tenant, threadId, traceId, AgentMessageRole.USER, AgentMessageType.TEXT, content, null, null);
    }

    public void appendAssistantText(final String tenant, final String threadId, final String traceId, final String content) {
        append(tenant, threadId, traceId, AgentMessageRole.ASSISTANT, AgentMessageType.TEXT, content, null, null);
    }

    public void appendToolCall(final String tenant, final String threadId, final String traceId, final String content, final AgentToolCall toolCall) {
        append(tenant, threadId, traceId, AgentMessageRole.ASSISTANT, AgentMessageType.TOOL_CALL, content, toolCall, null);
    }

    public void appendProposedAction(final String tenant, final String threadId, final String traceId, final String content, final AgentToolCall toolCall) {
        append(tenant, threadId, traceId, AgentMessageRole.ASSISTANT, AgentMessageType.PROPOSED_ACTION, content, toolCall, null);
    }

    public void appendToolResult(final String tenant, final String threadId, final String traceId, final AgentToolCall toolCall, final Map<String, Object> result) {
        append(tenant, threadId, traceId, AgentMessageRole.TOOL, AgentMessageType.TOOL_RESULT, null, toolCall, result);
    }

    public void appendArtefactDraft(final String tenant, final String threadId, final String traceId, final ArtefactDraft draft) {
        messageStore.append(
            AgentMessage.builder()
                .uid(newMessageUid())
                .tenant(tenant)
                .threadId(threadId)
                .role(AgentMessageRole.ASSISTANT)
                .type(AgentMessageType.ARTEFACT_DRAFT)
                .draft(draft)
                .traceId(traceId)
                .createdAt(Instant.now())
                .build()
        );
    }

    public void appendCancelled(final String tenant, final String threadId, final String traceId) {
        append(tenant, threadId, traceId, AgentMessageRole.SYSTEM, AgentMessageType.CANCELLED, null, null, null);
    }

    private String deriveTitle(final String tenant, final String threadId) {
        return messageStore.load(tenant, threadId).stream()
            .filter(m -> m.role() == AgentMessageRole.USER && m.type() == AgentMessageType.TEXT && m.content() != null)
            .findFirst()
            .map(m -> m.content().length() > MAX_TITLE_LENGTH ? m.content().substring(0, MAX_TITLE_LENGTH) + "…" : m.content())
            .orElse(null);
    }

    private void append(final String tenant, final String threadId, final String traceId, final AgentMessageRole role, final AgentMessageType type,
        final String content, final AgentToolCall toolCall, final Map<String, Object> toolResult) {
        messageStore.append(
            AgentMessage.builder()
                .uid(newMessageUid())
                .tenant(tenant)
                .threadId(threadId)
                .role(role)
                .type(type)
                .content(content)
                .toolCall(toolCall)
                .toolResult(toolResult)
                .traceId(traceId)
                .createdAt(Instant.now())
                .build()
        );
    }

    /**
     * Mints a monotonically-increasing message uid: a zero-padded per-node sequence prefix followed by
     * a random id. Message history is loaded ordered by this uid, so the prefix guarantees messages
     * sort in append order (a tool call always before its result).
     */
    private String newMessageUid() {
        return String.format("%019d-%s", sequence.incrementAndGet(), IdUtils.create());
    }
}
