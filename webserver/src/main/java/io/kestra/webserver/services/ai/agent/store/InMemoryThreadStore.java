package io.kestra.webserver.services.ai.agent.store;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import io.kestra.webserver.services.ai.agent.domain.AgentThread;
import io.kestra.webserver.services.ai.agent.domain.AgentThreadStatus;

import jakarta.inject.Singleton;

@Singleton
public class InMemoryThreadStore implements ThreadStore {
    private final Map<String, AgentThread> threads = new ConcurrentHashMap<>();

    private static String key(final String tenant, final String uid) {
        return tenant + "/" + uid;
    }

    @Override
    public AgentThread create(final AgentThread thread) {
        Objects.requireNonNull(thread, "thread");
        threads.put(key(thread.tenant(), thread.uid()), thread);
        return thread;
    }

    @Override
    public Optional<AgentThread> find(final String tenant, final String uid) {
        return Optional.ofNullable(threads.get(key(tenant, uid)))
            .filter(thread -> !thread.deleted());
    }

    @Override
    public boolean exists(final String tenant, final String uid) {
        return find(tenant, uid).isPresent();
    }

    @Override
    public AgentThread save(final AgentThread thread) {
        Objects.requireNonNull(thread, "thread");
        threads.put(key(thread.tenant(), thread.uid()), thread);
        return thread;
    }

    @Override
    public Optional<AgentThread> updateIf(final String tenant, final String uid,
        final AgentThreadStatus expected, final UnaryOperator<AgentThread> mutation) {
        AtomicReference<AgentThread> applied = new AtomicReference<>();
        threads.compute(key(tenant, uid), (k, existing) ->
        {
            if (existing == null || existing.deleted() || existing.status() != expected) {
                return existing;
            }
            AgentThread updated = Objects.requireNonNull(mutation.apply(existing), "mutation must not return null");
            applied.set(updated);
            return updated;
        });
        return Optional.ofNullable(applied.get());
    }
}
