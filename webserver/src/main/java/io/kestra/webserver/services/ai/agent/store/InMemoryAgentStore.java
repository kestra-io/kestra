package io.kestra.webserver.services.ai.agent.store;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentThreadStatus;
import io.kestra.webserver.services.ai.agent.AgentConfiguration;

import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Non-durable, in-process backing store for the Copilot: it holds each conversation (its
 * {@link AgentThread} plus the {@link AgentMessage} log) in memory only. It keeps just the live
 * conversations and evicts them once they are finished with — a conversation idle for longer than
 * {@code inMemoryConversationTtl} is dropped, and a hard {@code maxInMemoryConversations} cap evicts
 * the least-recently-active conversation so memory stays bounded. A thread and its messages share one
 * entry (keyed by the thread/{@code threadId} uid) and are evicted together. Durable, per-user thread
 * history and management are provided by a durable implementation of the repository interfaces when
 * one is registered, which then replaces this store.
 *
 * <p>
 * Access is guarded by coarse method-level synchronization: OSS runs a single node and turns are
 * single-flighted per thread, so contention is negligible and the simplicity is worth more than
 * throughput here.
 */
@Singleton
public class InMemoryAgentStore {

    private final Map<String, Conversation> conversations = new HashMap<>();
    private final Duration ttl;
    private final int maxConversations;

    @Inject
    public InMemoryAgentStore(final AgentConfiguration configuration) {
        this.ttl = configuration.inMemoryConversationTtl();
        this.maxConversations = configuration.maxInMemoryConversations();
    }

    // --- thread operations ---

    public synchronized AgentThread create(final AgentThread thread) {
        conversation(thread.uid()).thread = thread;
        evictStale();
        return thread;
    }

    public synchronized AgentThread save(final AgentThread thread) {
        conversation(thread.uid()).thread = thread;
        evictStale();
        return thread;
    }

    public synchronized AgentThread delete(final AgentThread thread) {
        AgentThread deleted = thread.toDeleted();
        save(deleted);
        return deleted;
    }

    public synchronized Optional<AgentThread> find(final String tenant, final String uid) {
        return liveThread(tenant, uid);
    }

    public synchronized boolean exists(final String tenant, final String uid) {
        return liveThread(tenant, uid).isPresent();
    }

    /**
     * In-memory equivalent of the compare-and-set turn guard: applies the mutation only if the thread
     * exists, is not deleted, its tenant matches, and its status equals {@code expected}. The whole
     * check-and-set runs under the store lock, so it is atomic on this node.
     */
    public synchronized Optional<AgentThread> updateIf(final String tenant, final String uid, final AgentThreadStatus expected, final UnaryOperator<AgentThread> mutation) {
        Optional<AgentThread> current = liveThread(tenant, uid);
        if (current.isEmpty() || current.get().status() != expected) {
            return Optional.empty();
        }
        AgentThread updated = Objects.requireNonNull(mutation.apply(current.get()), "mutation must not return null");
        Conversation conversation = conversations.get(uid);
        conversation.thread = updated;
        conversation.touch();
        return Optional.of(updated);
    }

    // --- message operations ---

    public synchronized AgentMessage append(final AgentMessage message) {
        conversation(message.threadId()).messages.add(message);
        evictStale();
        return message;
    }

    public synchronized List<AgentMessage> load(final String tenant, final String threadId) {
        Conversation conversation = conversations.get(threadId);
        if (conversation == null) {
            return List.of();
        }
        return conversation.messages.stream()
            // Messages carry their own tenant, so history is scoped by the message's tenant,
            // independent of any thread record.
            .filter(m -> Objects.equals(m.tenant(), tenant))
            .sorted(Comparator.comparing(AgentMessage::uid))
            .toList();
    }

    // --- internals ---

    /** The entry for {@code id}, creating an empty one (no thread yet) if absent, and touching it. */
    private Conversation conversation(final String id) {
        Conversation conversation = conversations.computeIfAbsent(id, k -> new Conversation());
        conversation.touch();
        return conversation;
    }

    private Optional<AgentThread> liveThread(final String tenant, final String uid) {
        Conversation conversation = conversations.get(uid);
        if (conversation == null || conversation.thread == null || conversation.thread.isDeleted()
            || !Objects.equals(conversation.thread.tenant(), tenant)) {
            return Optional.empty();
        }
        return Optional.of(conversation.thread);
    }

    /**
     * Drops conversations idle past the TTL, then, if still over the cap, the least-recently-active
     * ones. Called on every write so a finished conversation is reclaimed without a background sweep.
     */
    private void evictStale() {
        Instant cutoff = Instant.now().minus(ttl);
        conversations.values().removeIf(c -> c.lastActivity.isBefore(cutoff));
        int overflow = conversations.size() - maxConversations;
        if (overflow > 0) {
            conversations.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().lastActivity))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(conversations::remove);
        }
    }

    private static final class Conversation {
        @Nullable
        private AgentThread thread;
        private final List<AgentMessage> messages = new ArrayList<>();
        private Instant lastActivity = Instant.now();

        private void touch() {
            this.lastActivity = Instant.now();
        }
    }
}
