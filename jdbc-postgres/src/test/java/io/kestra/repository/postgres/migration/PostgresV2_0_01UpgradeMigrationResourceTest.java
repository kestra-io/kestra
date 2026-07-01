package io.kestra.repository.postgres.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresV2_0_01UpgradeMigrationResourceTest {
    @Test
    void shouldCreateExecutionIndexesConcurrently() throws IOException {
        // Given / When
        String migration = loadResource("/migrations/2.0.01-upgrade-postgres.sql");

        // Then
        assertThat(migration).contains(
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_executions_trigger_id ON executions (\"trigger_id\");",
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS executions_parent_id ON executions (\"deleted\", \"tenant_id\", \"parent_id\");"
        );
        assertThat(migration).doesNotContain(
            "CREATE INDEX IF NOT EXISTS idx_executions_trigger_id ON executions (\"trigger_id\");",
            "CREATE INDEX IF NOT EXISTS executions_parent_id ON executions (\"deleted\", \"tenant_id\", \"parent_id\");"
        );
    }

    private String loadResource(final String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
