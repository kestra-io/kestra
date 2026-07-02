package io.kestra.mcp;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.mcp.repositories.McpServerRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link McpServerCache}. Uses a real H2-backed repository and the real
 * cluster-event broadcast queue so the cache's {@code MCP_SERVER_CHANGED} subscription is
 * exercised end-to-end (no mocks).
 */
@KestraTest(environments = "h2")
@Property(name = "kestra.server-type", value = "WEBSERVER")
class McpServerCacheTest {

    private static final String TENANT = null;

    @Inject
    private McpServerCache cache;

    @Inject
    private McpServerRepositoryInterface mcpServerRepository;

    private String serverId;

    @BeforeEach
    void setUp() {
        serverId = "server-" + IdUtils.create().toLowerCase();
    }

    @Test
    void shouldReturnServerWhenGettingSavedServer() {
        // Given
        McpServer saved = mcpServerRepository.save(null, buildServer(serverId));

        // When
        Optional<McpServer> result = cache.get(TENANT, saved.id());

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(saved.id());
    }

    @Test
    void shouldReturnEmptyWhenGettingUnknownServer() {
        // When
        Optional<McpServer> result = cache.get(TENANT, "does-not-exist-" + IdUtils.create());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReFetchFromRepositoryWhenGettingAfterInvalidate() {
        // Given — populate the cache, then mutate the repository directly so the cached entry diverges
        McpServer saved = mcpServerRepository.save(null, buildServer(serverId));
        Optional<McpServer> cachedFirst = cache.get(TENANT, saved.id());
        assertThat(cachedFirst).map(McpServer::description).contains("Original");

        McpServer renamed = withDescription(saved, "Updated");
        mcpServerRepository.save(saved, renamed);

        // When — explicit invalidate (rather than waiting for the async cluster event)
        cache.invalidate(TENANT, saved.id());

        // Then — the cache now returns the updated value from the repository
        Optional<McpServer> cachedAfter = cache.get(TENANT, saved.id());
        assertThat(cachedAfter).map(McpServer::description).contains("Updated");
    }

    @Test
    void shouldNotifyListenerWhenInvalidating() {
        // Given
        AtomicInteger calls = new AtomicInteger();
        cache.addInvalidationListener((tenantId, sid) -> {
            if (sid.equals(serverId)) {
                calls.incrementAndGet();
            }
        });

        // When
        cache.invalidate(TENANT, serverId);

        // Then
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void shouldStillNotifySubsequentListenersWhenAListenerThrows() {
        // Given
        AtomicInteger reached = new AtomicInteger();
        cache.addInvalidationListener((t, s) -> {
            if (s.equals(serverId)) {
                throw new RuntimeException("boom");
            }
        });
        cache.addInvalidationListener((t, s) -> {
            if (s.equals(serverId)) {
                reached.incrementAndGet();
            }
        });

        // When
        cache.invalidate(TENANT, serverId);

        // Then — exception in the first listener must not prevent the second from running
        assertThat(reached.get()).isEqualTo(1);
    }

    @Test
    void shouldInvalidateCacheAndNotifyListenersWhenRepositoryEmitsChangedEvent() {
        // Given — server saved + cached + listener registered
        McpServer saved = mcpServerRepository.save(null, buildServer(serverId));
        cache.get(TENANT, saved.id()); // populate

        AtomicInteger listenerCalls = new AtomicInteger();
        cache.addInvalidationListener((tenantId, sid) -> {
            if (sid.equals(saved.id())) {
                listenerCalls.incrementAndGet();
            }
        });

        // When — repo update broadcasts a MCP_SERVER_CHANGED cluster event
        mcpServerRepository.save(saved, withDescription(saved, "Updated via cluster event"));

        // Then — the listener is invoked and the cache returns the fresh value
        Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> {
                assertThat(listenerCalls.get()).isGreaterThanOrEqualTo(1);
                Optional<McpServer> refreshed = cache.get(TENANT, saved.id());
                assertThat(refreshed).map(McpServer::description).contains("Updated via cluster event");
            });
    }

    @Test
    void shouldInvalidateCacheWhenRepositoryDeletesServer() {
        // Given
        McpServer saved = mcpServerRepository.save(null, buildServer(serverId));
        assertThat(cache.get(TENANT, saved.id())).isPresent();

        // When
        mcpServerRepository.delete(TENANT, saved.id());

        // Then — once the cluster event is processed, the cache no longer returns the server
        Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() ->
                assertThat(cache.get(TENANT, saved.id())).isEmpty()
            );
    }

    private static McpServer buildServer(String id) {
        return new McpServer(
            TENANT, id, "Original", null,
            McpServer.ServerType.PRIVATE, McpServer.AuthType.BASIC, null, null,
            false, false, false, null, null
        );
    }

    private static McpServer withDescription(McpServer original, String description) {
        return new McpServer(
            original.tenantId(), original.id(), description, original.instructions(),
            original.serverType(), original.authType(), original.oauthProvider(),
            original.oauthScopesSupported(),
            original.disabled(), original.isDefault(), original.deleted(),
            original.created(), original.updated()
        );
    }
}
