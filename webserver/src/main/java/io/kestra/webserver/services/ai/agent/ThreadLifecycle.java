package io.kestra.webserver.services.ai.agent;

import java.time.Instant;
import java.util.Optional;

import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.domain.AgentThread;
import io.kestra.webserver.services.ai.agent.domain.AgentThreadStatus;
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

    public Optional<AgentThread> find(final String tenant, final String uid) {
        return threadStore.find(tenant, uid);
    }

    public AgentThread markRunning(final AgentThread thread, final AgentMode mode) {
        return threadStore.save(thread.toBuilder()
            .status(AgentThreadStatus.RUNNING)
            .ownerNodeId(nodeId)
            .mode(mode)
            .updatedAt(Instant.now())
            .build());
    }

    public AgentThread markAwaiting(final AgentThread thread) {
        return threadStore.save(thread.withStatus(AgentThreadStatus.AWAITING_CONFIRMATION).withUpdatedAt(Instant.now()));
    }

    public AgentThread finish(final AgentThread thread, final String derivedTitle) {
        Instant now = Instant.now();
        AgentThread.AgentThreadBuilder builder = thread.toBuilder()
            .status(AgentThreadStatus.IDLE)
            .ownerNodeId(null)
            .lastTurnAt(now)
            .updatedAt(now);
        if (thread.title() == null) {
            builder.title(derivedTitle);
        }
        return threadStore.save(builder.build());
    }

    public void resetToIdle(final AgentThread thread) {
        threadStore.find(thread.tenant(), thread.uid()).ifPresent(t ->
            threadStore.save(t.withStatus(AgentThreadStatus.IDLE).withOwnerNodeId(null).withUpdatedAt(Instant.now()))
        );
    }
}
