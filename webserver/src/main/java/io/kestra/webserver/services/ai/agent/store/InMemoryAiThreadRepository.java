package io.kestra.webserver.services.ai.agent.store;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentThreadStatus;
import io.kestra.core.ai.agent.repositories.AiThreadRepositoryInterface;

import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * In-memory {@link AiThreadRepositoryInterface}: delegates to the ephemeral {@link InMemoryAgentStore}.
 * Declared {@link Secondary} so that a durable implementation, when one is registered, takes
 * precedence; when none is, this is the only candidate and is used.
 */
@Singleton
@Secondary
public class InMemoryAiThreadRepository implements AiThreadRepositoryInterface {

    private final InMemoryAgentStore store;

    @Inject
    public InMemoryAiThreadRepository(final InMemoryAgentStore store) {
        this.store = store;
    }

    @Override
    public Optional<AgentThread> find(final String tenant, final String uid) {
        return store.find(tenant, uid);
    }

    /**
     * Always empty: this in-memory store exposes no browsable conversation history. A live conversation
     * stays reachable by its id for the duration of the session (so {@link #find} still serves the
     * active chat), but prior ones are never listed — once the user moves on from a chat it is not
     * resurfaced, and it is reclaimed by the store's idle eviction.
     */
    @Override
    public List<AgentThread> findAllForUser(final String tenant, final String userId) {
        return List.of();
    }

    @Override
    public boolean exists(final String tenant, final String uid) {
        return store.exists(tenant, uid);
    }

    @Override
    public AgentThread create(final AgentThread thread) {
        return store.create(thread);
    }

    @Override
    public AgentThread save(final AgentThread thread) {
        return store.save(thread);
    }

    @Override
    public AgentThread delete(final AgentThread thread) {
        return store.delete(thread);
    }

    @Override
    public Optional<AgentThread> updateIf(final String tenant, final String uid, final AgentThreadStatus expected, final UnaryOperator<AgentThread> mutation) {
        return store.updateIf(tenant, uid, expected, mutation);
    }
}
