package io.kestra.core.mcp.repositories;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.mcp.services.McpServerService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.kestra.core.repositories.ArrayListTotal;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(transactional = false)
public abstract class AbstractMcpServerRepositoryTest {

    @Inject
    private McpServerRepositoryInterface mcpServerRepository;

    @Inject
    private McpServerService mcpServerService;

    @BeforeEach
    void resetListener() {
        McpServerListener.reset();
    }

    @Test
    void shouldPersistWithTimestampsWhenSavingNewMcp() {
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
    void shouldReturnMcpWhenGettingExistingMcp() {
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
    void shouldReturnEmptyWhenGettingUnknownMcp() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // When
        Optional<McpServer> found = mcpServerRepository.get(tenant, "non-existent-name");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenGettingMcpFromOtherTenant() {
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
    void shouldReturnEmptyWhenGettingDeletedMcp() {
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
    void shouldPersistChangesAndPreserveCreatedDateWhenUpdatingExistingMcp() {
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
    void shouldReturnPreviousMcpWhenSavingUnchangedMcp() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpServer original = mcpServerRepository.save(null, createMcpServer(tenant));

        // When
        McpServer result = mcpServerRepository.save(original, original);

        // Then
        assertThat(result).isEqualTo(original);
    }

    @Test
    void shouldSoftDeleteMcpAndMakeItInvisibleWhenDeleting() {
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
    void shouldReturnEmptyWhenDeletingUnknownMcp() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // When
        Optional<McpServer> result = mcpServerRepository.delete(tenant, "non-existent-name");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAllMcpsWhenListing() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        mcpServerRepository.save(null, createMcpServer(tenant));
        mcpServerRepository.save(null, createMcpServer(tenant));

        // When
        ArrayListTotal<McpServer> results = mcpServerRepository.find(Pageable.from(1, 10), tenant);

        // Then
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.getTotal()).isEqualTo(2);
    }

    @Test
    void shouldExcludeDeletedMcpsWhenListing() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpServer toDelete = mcpServerRepository.save(null, createMcpServer(tenant));
        mcpServerRepository.save(null, createMcpServer(tenant));
        mcpServerRepository.delete(tenant, toDelete.id());

        // When
        ArrayListTotal<McpServer> results = mcpServerRepository.find(Pageable.from(1, 10), tenant);

        // Then
        assertThat(results.size()).isEqualTo(1);
    }

    @Test
    void shouldReturnOnlyCurrentTenantMcpsWhenListing() {
        // Given
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        mcpServerRepository.save(null, createMcpServer(tenant1));
        mcpServerRepository.save(null, createMcpServer(tenant2));

        // When / Then
        assertThat(mcpServerRepository.find(Pageable.from(1, 10), tenant1).size()).isEqualTo(1);
        assertThat(mcpServerRepository.find(Pageable.from(1, 10), tenant2).size()).isEqualTo(1);
    }

    @Test
    void shouldCreateDefaultServerWhenNoDefaultServerExists() {
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
    void shouldBeIdempotentWhenDefaultServerAlreadyExists() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        mcpServerService.createDefaultMcpServerIfNotExist(tenant);

        // When — call again
        mcpServerService.createDefaultMcpServerIfNotExist(tenant);

        // Then — exactly one default server, no duplicate
        ArrayListTotal<McpServer> results = mcpServerRepository.find(Pageable.from(1, 100), tenant);
        long defaultCount = results.stream().filter(McpServer::isDefault).count();
        assertThat(defaultCount).isEqualTo(1);
    }

    @Test
    void shouldReturnAllMcpsFromAllTenantsWhenListingAll() {
        // Given
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        mcpServerRepository.save(null, createMcpServer(tenant1));
        mcpServerRepository.save(null, createMcpServer(tenant2));

        // When
        ArrayListTotal<McpServer> results = mcpServerRepository.findForAllTenants(Pageable.from(1, 100));

        // Then — both tenants' servers are present
        long tenant1Count = results.stream().filter(s -> tenant1.equals(s.tenantId())).count();
        long tenant2Count = results.stream().filter(s -> tenant2.equals(s.tenantId())).count();
        assertThat(tenant1Count).isEqualTo(1);
        assertThat(tenant2Count).isEqualTo(1);
    }

    @Test
    void shouldExcludeDeletedMcpsWhenListingAll() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpServer toDelete = mcpServerRepository.save(null, createMcpServer(tenant));
        mcpServerRepository.save(null, createMcpServer(tenant));
        mcpServerRepository.delete(tenant, toDelete.id());

        // When
        ArrayListTotal<McpServer> results = mcpServerRepository.findForAllTenants(Pageable.from(1, 100));

        // Then — deleted record is excluded
        long tenantCount = results.stream().filter(s -> tenant.equals(s.tenantId())).count();
        assertThat(tenantCount).isEqualTo(1);
    }

    @Test
    void shouldPublishCreateEventWhenSavingNewMcp() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpServer mcp = createMcpServer(tenant);

        // When
        mcpServerRepository.save(null, mcp);

        // Then
        List<CrudEvent<McpServer>> events = McpServerListener.filterByTenant(tenant);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo(CrudEventType.CREATE);
        assertThat(events.get(0).getModel().id()).isEqualTo(mcp.id());
    }

    @Test
    void shouldPublishUpdateEventWhenSavingExistingMcp() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpServer original = mcpServerRepository.save(null, createMcpServer(tenant));
        McpServerListener.reset();
        McpServer updated = new McpServer(tenant, original.id(), "Updated", null, null, null, true, false, false, null, null);

        // When
        mcpServerRepository.save(original, updated);

        // Then
        List<CrudEvent<McpServer>> events = McpServerListener.filterByTenant(tenant);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo(CrudEventType.UPDATE);
        assertThat(events.get(0).getModel().id()).isEqualTo(original.id());
    }

    @Test
    void shouldPublishDeleteEventWhenDeletingExistingMcp() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        McpServer saved = mcpServerRepository.save(null, createMcpServer(tenant));
        McpServerListener.reset();

        // When
        mcpServerRepository.delete(tenant, saved.id());

        // Then
        List<CrudEvent<McpServer>> events = McpServerListener.filterByTenant(tenant);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getType()).isEqualTo(CrudEventType.DELETE);
        assertThat(events.get(0).getPreviousModel().id()).isEqualTo(saved.id());
    }

    private static McpServer createMcpServer(String tenantId) {
        String id = "test-mcp-" + IdUtils.create().toLowerCase();
        return new McpServer(tenantId, id, "A test MCP server", null, null, null, false, false, false, null, null);
    }

    @Singleton
    public static class McpServerListener implements ApplicationEventListener<CrudEvent<McpServer>> {
        private static List<CrudEvent<McpServer>> emits = new CopyOnWriteArrayList<>();

        @Override
        public void onApplicationEvent(CrudEvent<McpServer> event) {
            if (
                (event.getModel() != null && event.getModel() instanceof McpServer) ||
                    (event.getPreviousModel() != null && event.getPreviousModel() instanceof McpServer)
            ) {
                emits.add(event);
            }
        }

        public static void reset() {
            emits = new CopyOnWriteArrayList<>();
        }

        public static List<CrudEvent<McpServer>> filterByTenant(String tenantId) {
            return emits.stream()
                .filter(
                    e -> (e.getPreviousModel() != null && tenantId.equals(e.getPreviousModel().tenantId())) ||
                        (e.getModel() != null && tenantId.equals(e.getModel().tenantId()))
                )
                .toList();
        }
    }
}
