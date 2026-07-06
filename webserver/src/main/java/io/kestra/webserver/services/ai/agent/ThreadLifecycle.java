package io.kestra.webserver.services.ai.agent;

import java.time.Instant;
import java.util.Optional;

import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.agent.domain.Mode;
import io.kestra.webserver.services.ai.agent.domain.Thread;
import io.kestra.webserver.services.ai.agent.domain.ThreadStatus;
import io.kestra.webserver.services.ai.agent.store.ThreadStore;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ThreadLifecycle {
    private final ThreadStore threadStore;
    private final String nodeId = "node-" + IdUtils.create();

    @Inject
    public ThreadLifecycle(final ThreadStore threadStore) {
        this.threadStore = threadStore;
    }

    public Optional<Thread> find(final String tenant, final String uid) {
        return threadStore.find(tenant, uid);
    }

    public Thread markRunning(final Thread thread, final Mode mode) {
        return threadStore.save(thread.toBuilder()
            .status(ThreadStatus.RUNNING)
            .ownerNodeId(nodeId)
            .mode(mode)
            .updatedAt(Instant.now())
            .build());
    }

    public Thread markAwaiting(final Thread thread) {
        return threadStore.save(thread.withStatus(ThreadStatus.AWAITING_CONFIRMATION).withUpdatedAt(Instant.now()));
    }

    public Thread finish(final Thread thread, final String derivedTitle) {
        Instant now = Instant.now();
        Thread.ThreadBuilder builder = thread.toBuilder()
            .status(ThreadStatus.IDLE)
            .ownerNodeId(null)
            .lastTurnAt(now)
            .updatedAt(now);
        if (thread.title() == null) {
            builder.title(derivedTitle);
        }
        return threadStore.save(builder.build());
    }

    public void resetToIdle(final Thread thread) {
        threadStore.find(thread.tenant(), thread.uid()).ifPresent(t ->
            threadStore.save(t.withStatus(ThreadStatus.IDLE).withOwnerNodeId(null).withUpdatedAt(Instant.now()))
        );
    }
}
