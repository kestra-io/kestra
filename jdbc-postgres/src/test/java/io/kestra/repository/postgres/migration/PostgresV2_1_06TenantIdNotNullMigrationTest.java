package io.kestra.repository.postgres.migration;

import org.junit.jupiter.api.Test;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest(transactional = false)
class PostgresV2_1_06TenantIdNotNullMigrationTest {

    @Inject
    V2_1_06TenantIdNotNullMigration migration;

    @Test
    void migrationProperties() {
        assertThat(migration.scriptId()).isEqualTo("2.1.06-tenant-id-not-null");
        assertThat(migration.sqlResources()).containsExactly("/migrations/2.1.06-tenant-id-not-null-postgres.sql");
    }
}
