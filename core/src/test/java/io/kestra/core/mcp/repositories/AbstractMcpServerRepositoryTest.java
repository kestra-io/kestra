package io.kestra.core.mcp.repositories;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.mcp.services.McpServerService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import io.kestra.core.repositories.ArrayListTotal;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(transactional = false)
public abstract class AbstractMcpServerRepositoryTest {

    @Inject
    private McpServerRepositoryInterface mcpServerRepository;

    @Inject
    private McpServerService mcpServerService;

    @Test
    void givenNewMcpWhenSaveThenPersistedWithTimestamps() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpServer mcpServer = createMcpServer(tenant);

        // When
        McpServer saved = mcpServerRepository.save(null, mcpServer);

        // Then
        assertThat(saved.id()).isEqualTo(mcpServer.id());
        assertThat(saved.disabled()).isFalse();
        assertThat(saved.deleted()).isFalse();
        assertThat(saved.created()).isNotNull();
        assertThat(saved.updated()).isNotNull();
    }

    @Test
    void givenExistingMcpWhenGetThenReturned() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpServer saved = mcpServerRepository.save(null, createMcpServer(tenant));

        // When
        Optional<McpServer> found = mcpServerRepository.get(tenant, saved.id());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(saved.id());
    }

    @Test
    void givenUnknownNameWhenGetThenEmpty() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // When
        Optional<McpServer> found = mcpServerRepository.get(tenant, "non-existent-name");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void givenMcpFromOtherTenantWhenGetThenEmpty() {
        // Given
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpServer saved = mcpServerRepository.save(null, createMcpServer(tenant1));

        // When
        Optional<McpServer> found = mcpServerRepository.get(tenant2, saved.id());

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void givenDeletedMcpWhenGetThenEmpty() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpServer saved = mcpServerRepository.save(null, createMcpServer(tenant));
        mcpServerRepository.delete(tenant, saved.id());

        // When
        Optional<McpServer> found = mcpServerRepository.get(tenant, saved.id());

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void givenExistingMcpWhenUpdateThenChangesPersistedAndCreatedPreserved() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpServer original = mcpServerRepository.save(null, createMcpServer(tenant));
        McpServer updated = new McpServer(tenant,
            original.id(), "Updated description", null, null, null, true, false, false, null, null);

        // When
        McpServer result = mcpServerRepository.save(original, updated);

        // Then
        assertThat(result.id()).isEqualTo(original.id());
        assertThat(result.description()).isEqualTo("Updated description");
        assertThat(result.disabled()).isTrue();
        assertThat(result.created()).isEqualTo(original.created());
        assertThat(result.updated()).isAfterOrEqualTo(original.updated());
    }

    @Test
    void givenUnchangedMcpWhenSaveThenPreviousReturned() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpServer original = mcpServerRepository.save(null, createMcpServer(tenant));

        // When
        McpServer result = mcpServerRepository.save(original, original);

        // Then
        assertThat(result).isEqualTo(original);
    }

    @Test
    void givenExistingMcpWhenDeleteThenSoftDeletedAndNoLongerVisible() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpServer saved = mcpServerRepository.save(null, createMcpServer(tenant));

        // When
        Optional<McpServer> deleted = mcpServerRepository.delete(tenant, saved.id());

        // Then
        assertThat(deleted).isPresent();
        assertThat(deleted.get().deleted()).isTrue();
        assertThat(mcpServerRepository.get(tenant, saved.id())).isEmpty();
    }

    @Test
    void givenUnknownNameWhenDeleteThenEmpty() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // When
        Optional<McpServer> result = mcpServerRepository.delete(tenant, "non-existent-name");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void givenMultipleMcpsWhenListThenAllReturned() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        mcpServerRepository.save(null, createMcpServer(tenant));
        mcpServerRepository.save(null, createMcpServer(tenant));

        // When
        ArrayListTotal<McpServer> results = mcpServerRepository.listAll(Pageable.from(1, 10), tenant);

        // Then
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.getTotal()).isEqualTo(2);
    }

    @Test
    void givenDeletedMcpWhenListThenExcludedFromResults() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpServer toDelete = mcpServerRepository.save(null, createMcpServer(tenant));
        mcpServerRepository.save(null, createMcpServer(tenant));
        mcpServerRepository.delete(tenant, toDelete.id());

        // When
        ArrayListTotal<McpServer> results = mcpServerRepository.listAll(Pageable.from(1, 10), tenant);

        // Then
        assertThat(results.size()).isEqualTo(1);
    }

    @Test
    void givenMcpsAcrossTenantsWhenListThenOnlyCurrentTenantReturned() {
        // Given
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        mcpServerRepository.save(null, createMcpServer(tenant1));
        mcpServerRepository.save(null, createMcpServer(tenant2));

        // When / Then
        assertThat(mcpServerRepository.listAll(Pageable.from(1, 10), tenant1).size()).isEqualTo(1);
        assertThat(mcpServerRepository.listAll(Pageable.from(1, 10), tenant2).size()).isEqualTo(1);
    }

    @Test
    void givenNoDefaultServer_whenEnsureDefault_thenDefaultServerCreated() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // When
        mcpServerService.createDefaultMcpServerIfNotExist(tenant);

        // Then
        Optional<McpServer> found = mcpServerRepository.get(tenant, McpServer.DEFAULT_ID);
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(McpServer.DEFAULT_ID);
        assertThat(found.get().isDefault()).isTrue();
        assertThat(found.get().disabled()).isFalse();
        assertThat(found.get().created()).isNotNull();
    }

    @Test
    void givenExistingDefaultServer_whenEnsureDefault_thenIdempotent() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        mcpServerService.createDefaultMcpServerIfNotExist(tenant);

        // When — call again
        mcpServerService.createDefaultMcpServerIfNotExist(tenant);

        // Then — exactly one default server, no duplicate
        ArrayListTotal<McpServer> results = mcpServerRepository.listAll(Pageable.from(1, 100), tenant);
        long defaultCount = results.stream().filter(McpServer::isDefault).count();
        assertThat(defaultCount).isEqualTo(1);
    }

    private static McpServer createMcpServer(String tenantId) {
        String id = "test-mcp-" + IdUtils.create().toLowerCase();
        return new McpServer(tenantId, id, "A test MCP server", null, null, null, false, false, false, null, null);
    }
}
