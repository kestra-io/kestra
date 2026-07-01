package io.kestra.repository.mysql.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MysqlV2_0_01UpgradeMigrationResourceTest {
    @Test
    void shouldCreateExecutionIndexesWithOnlineDdl() throws IOException {
        // Given / When
        String migration = loadResource("/migrations/2.0.01-upgrade-mysql.sql");

        // Then
        assertThat(migration).contains(
            "ALTER TABLE executions ADD INDEX idx_executions_trigger_id (`trigger_id`), ALGORITHM=INPLACE, LOCK=NONE",
            "ALTER TABLE executions ADD INDEX executions_parent_id (`deleted`, `tenant_id`, `parent_id`), ALGORITHM=INPLACE, LOCK=NONE"
        );
        assertThat(migration).doesNotContain(
            "CREATE INDEX idx_executions_trigger_id ON executions (`trigger_id`)",
            "CREATE INDEX executions_parent_id ON executions (`deleted`, `tenant_id`, `parent_id`)"
        );
    }

    private String loadResource(final String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
