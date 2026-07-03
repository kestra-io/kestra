package io.kestra.repository.postgres;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.jooq.impl.DSL;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.repositories.log.LogRepositoryInterfaceFactory;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the dedicated-datasource path on real PostgreSQL: with {@code kestra.logs.type=postgres} +
 * a distinct {@code kestra.logs.postgres.url} (a separate database), the log store writes/reads that
 * database (created by the {@code 0-init-logs-postgres} migration, incl. its prerequisite type +
 * functions), and the primary database receives none of those logs.
 */
@Tag("integration")
@MicronautTest
@Property(name = "kestra.logs.type", value = "postgres")
@Property(name = "kestra.logs.postgres.url", value = "jdbc:postgresql://localhost:5432/logs_dedicated")
@Property(name = "kestra.logs.postgres.username", value = "kestra")
@Property(name = "kestra.logs.postgres.password", value = "k3str4")
class PostgresLogStoreDedicatedTest {

    @Inject
    LogRepositoryInterfaceFactory logRepositoryInterfaceFactory;

    @Inject
    @Named("logs")
    PostgresRepository<LogEntry> primaryLogs;

    @Test
    void shouldWriteAndReadFromDedicatedDatabaseAndNotThePrimary() {
        LogRepositoryInterface dedicated = logRepositoryInterfaceFactory.make("postgres", Map.of());
        String executionId = "dedicated-exec-" + Instant.now().toEpochMilli();
        LogEntry log = LogEntry.builder()
            .tenantId("main")
            .namespace("io.kestra.tests")
            .flowId("dedicated-flow")
            .executionId(executionId)
            .level(Level.INFO)
            .timestamp(Instant.now())
            .message("hello dedicated postgres log store")
            .build();

        dedicated.save(log);

        List<LogEntry> fromDedicated = dedicated.findByExecutionId("main", executionId, Level.TRACE);
        assertThat(fromDedicated).hasSize(1);
        assertThat(fromDedicated.getFirst().getMessage()).isEqualTo("hello dedicated postgres log store");

        assertThat(primaryLogs.count(DSL.field(DSL.name("execution_id")).eq(executionId))).isZero();
    }
}
