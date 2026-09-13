package io.kestra.webserver.services.ai.agent.store;

import java.util.List;

import io.kestra.core.ai.agent.models.AgentMessage;
import io.kestra.core.ai.agent.repositories.AiMessageRepositoryInterface;

import io.micronaut.context.annotation.Secondary;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * In-memory {@link AiMessageRepositoryInterface}: delegates to the ephemeral
 * {@link InMemoryAgentStore}, sharing the same per-conversation storage as
 * {@link InMemoryAiThreadRepository} so a thread and its messages are evicted together. Declared
 * {@link Secondary} so that a durable implementation, when one is registered, takes precedence.
 */
@Singleton
@Secondary
public class InMemoryAiMessageRepository implements AiMessageRepositoryInterface {

    private final InMemoryAgentStore store;

    @Inject
    public InMemoryAiMessageRepository(final InMemoryAgentStore store) {
        this.store = store;
    }

    @Override
    public AgentMessage append(final AgentMessage message) {
        return store.append(message);
    }

    @Override
    public List<AgentMessage> load(final String tenant, final String threadId) {
        return store.load(tenant, threadId);
    }
}
