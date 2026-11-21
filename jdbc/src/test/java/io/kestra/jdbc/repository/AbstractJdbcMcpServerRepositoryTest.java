package io.kestra.jdbc.repository;

import org.junit.jupiter.api.Test;

import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.mcp.repositories.AbstractMcpServerRepositoryTest;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static io.kestra.jdbc.repository.AbstractJdbcRepository.field;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJdbcMcpServerRepositoryTest extends AbstractMcpServerRepositoryTest {
    @Inject
    private AbstractJdbcMcpServerRepository jdbcMcpServerRepository;

    @Test
    void shouldMatchOnlyExistingItemsOfTheTenantWhenCheckingExistence() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String id = "test-mcp-" + IdUtils.create().toLowerCase();
        jdbcMcpServerRepository.save((McpServer) null, new McpServer(tenant, id, "A test MCP server", null, null, null, null, null, false, false, false, null, null));

        assertThat(jdbcMcpServerRepository.exists(tenant, field("id", String.class).eq(id))).isTrue();
        assertThat(jdbcMcpServerRepository.exists(tenant, field("id", String.class).eq("unknown"))).isFalse();
        assertThat(jdbcMcpServerRepository.exists("other-" + tenant, field("id", String.class).eq(id))).isFalse();
    }
}
