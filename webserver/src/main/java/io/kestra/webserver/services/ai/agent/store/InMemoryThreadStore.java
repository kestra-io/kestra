package io.kestra.webserver.services.ai.agent.store;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.kestra.webserver.services.ai.agent.domain.Thread;

import jakarta.inject.Singleton;

@Singleton
public class InMemoryThreadStore implements ThreadStore {
    private final Map<String, Thread> threads = new ConcurrentHashMap<>();

    private static String key(final String tenant, final String uid) {
        return tenant + "/" + uid;
    }

    @Override
    public Thread create(final Thread thread) {
        Objects.requireNonNull(thread, "thread");
        threads.put(key(thread.tenant(), thread.uid()), thread);
        return thread;
    }

    @Override
    public Optional<Thread> find(final String tenant, final String uid) {
        return Optional.ofNullable(threads.get(key(tenant, uid)))
            .filter(thread -> !thread.deleted());
    }

    @Override
    public boolean exists(final String tenant, final String uid) {
        return find(tenant, uid).isPresent();
    }

    @Override
    public Thread save(final Thread thread) {
        Objects.requireNonNull(thread, "thread");
        threads.put(key(thread.tenant(), thread.uid()), thread);
        return thread;
    }
}
