package io.kestra.core.mcp.repositories;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.mcp.models.McpSession;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(transactional = false)
public abstract class AbstractMcpSessionRepositoryTest {

    @Inject
    private McpSessionRepositoryInterface mcpSessionRepository;

    @Test
    void shouldPersistSessionWhenSavingNewSession() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpSession session = createSession(tenant);

        // When
        McpSession saved = mcpSessionRepository.save(session);

        // Then
        assertThat(saved.sessionId()).isEqualTo(session.sessionId());
        assertThat(saved.serverId()).isEqualTo(session.serverId());
        assertThat(saved.sseNode()).isEqualTo(session.sseNode());
        assertThat(saved.tenantId()).isEqualTo(tenant);
    }

    @Test
    void shouldReturnSessionWhenFindingExistingSession() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpSession saved = mcpSessionRepository.save(createSession(tenant));

        // When
        Optional<McpSession> found = mcpSessionRepository.find(
            tenant, saved.serverId(), saved.sessionId()
        );

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().sessionId()).isEqualTo(saved.sessionId());
        assertThat(found.get().sseNode()).isEqualTo(saved.sseNode());
    }

    @Test
    void shouldReturnEmptyWhenFindingUnknownSession() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // When
        Optional<McpSession> found = mcpSessionRepository.find(
            tenant, "server-id", IdUtils.create()
        );

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldReturnAllSessionsWhenFindingByServerId() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String namespace = "io.kestra.test";
        String serverId = "server-" + IdUtils.create();

        mcpSessionRepository.save(createSession(tenant, serverId, "node-1"));
        mcpSessionRepository.save(createSession(tenant, serverId, "node-1"));
        // Different server — must not appear in results
        mcpSessionRepository.save(createSession(tenant, "other-server", "node-2"));

        // When
        List<McpSession> results = mcpSessionRepository.findByServerId(tenant, serverId);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(s -> s.serverId().equals(serverId));
    }

    @Test
    void shouldReturnOnlyMatchingNodeSessionsWhenFindingBySseNode() {
        // Given
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String targetNode = "node-" + IdUtils.create();

        mcpSessionRepository.save(createSession(tenant1, "srv-1", targetNode));
        mcpSessionRepository.save(createSession(tenant2,"srv-2", targetNode));
        // Different node — must not appear
        mcpSessionRepository.save(createSession(tenant1,"srv-3", "other-node"));

        // When
        List<McpSession> results = mcpSessionRepository.findBySseNode(targetNode);

        // Then
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        assertThat(results).allMatch(s -> s.sseNode().equals(targetNode));
    }

    @Test
    void shouldRemoveAndReturnSessionWhenDeletingExistingSession() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpSession saved = mcpSessionRepository.save(createSession(tenant));

        // When
        Optional<McpSession> deleted = mcpSessionRepository.delete(tenant, saved.sessionId());

        // Then
        assertThat(deleted).isPresent();
        assertThat(deleted.get().sessionId()).isEqualTo(saved.sessionId());
        assertThat(mcpSessionRepository.find(tenant, saved.serverId(), saved.sessionId()))
            .isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenDeletingUnknownSession() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // When
        Optional<McpSession> result = mcpSessionRepository.delete(tenant, IdUtils.create());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnOnlyCurrentTenantSessionsWhenFindingByServerId() {
        // Given
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String namespace = "io.kestra.test";
        String serverId = "server-" + IdUtils.create();

        mcpSessionRepository.save(createSession(tenant1, serverId, "node-1"));
        mcpSessionRepository.save(createSession(tenant2, serverId, "node-2"));

        // When / Then
        assertThat(mcpSessionRepository.findByServerId(tenant1, serverId)).hasSize(1);
        assertThat(mcpSessionRepository.findByServerId(tenant2, serverId)).hasSize(1);
    }

    @Test
    void shouldUpdateSessionWhenSavingExistingSessionAgain() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpSession original = mcpSessionRepository.save(createSession(tenant));
        McpSession updated = new McpSession(
            original.tenantId(), original.serverId(),
            original.sessionId(), "new-node", null, false
        );

        // When
        mcpSessionRepository.save(updated);

        // Then
        Optional<McpSession> found = mcpSessionRepository.find(
            tenant, original.serverId(), original.sessionId()
        );
        assertThat(found).isPresent();
        assertThat(found.get().sseNode()).isEqualTo("new-node");
    }

    @Test
    void shouldDeleteSessionWhenSessionIsOlderThan48Hours() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpSession session = mcpSessionRepository.save(createSession(tenant));
        backdateCreatedAt(session.sessionId(), Instant.now().minus(3, ChronoUnit.DAYS));

        // When
        int purged = mcpSessionRepository.purgeOlderThan(Instant.now().minus(48, ChronoUnit.HOURS));

        // Then
        assertThat(purged).isGreaterThanOrEqualTo(1);
        assertThat(mcpSessionRepository.find(tenant, session.serverId(), session.sessionId())).isEmpty();
    }

    @Test
    void shouldRetainSessionWhenSessionIsNewerThan48Hours() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpSession session = mcpSessionRepository.save(createSession(tenant));

        // When
        int purged = mcpSessionRepository.purgeOlderThan(Instant.now().minus(48, ChronoUnit.HOURS));

        // Then — the just-created session must still exist
        assertThat(mcpSessionRepository.find(tenant, session.serverId(), session.sessionId())).isPresent();
        // purge count may be non-zero due to sessions from other tests, but this session was not touched
    }

    @Test
    void shouldDeleteOnlyOldSessionsWhenPurgingOlderThan() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpSession old = mcpSessionRepository.save(createSession(tenant));
        McpSession recent = mcpSessionRepository.save(createSession(tenant));
        backdateCreatedAt(old.sessionId(), Instant.now().minus(3, ChronoUnit.DAYS));

        // When
        mcpSessionRepository.purgeOlderThan(Instant.now().minus(48, ChronoUnit.HOURS));

        // Then
        assertThat(mcpSessionRepository.find(tenant, old.serverId(), old.sessionId())).isEmpty();
        assertThat(mcpSessionRepository.find(tenant, recent.serverId(), recent.sessionId())).isPresent();
    }

    /** Sets {@code created_at} to {@code timestamp} for the given session. Implemented by JDBC-module subclasses. */
    protected abstract void backdateCreatedAt(String sessionId, Instant timestamp);

    private static McpSession createSession(String tenantId) {
        return createSession(tenantId, "server-" + IdUtils.create(), "node-1");
    }

    private static McpSession createSession(String tenantId, String serverId, String sseNode) {
        return new McpSession(tenantId, serverId, IdUtils.create(), sseNode, null, false);
    }
}
