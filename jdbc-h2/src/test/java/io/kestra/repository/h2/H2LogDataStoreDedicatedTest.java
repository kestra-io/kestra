package io.kestra.repository.h2;

import java.time.Instant;
import java.util.List;

import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.repositories.log.LogDataStoreInterfaceFactory;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the dedicated-datasource path: with {@code kestra.logs.type=h2} + a distinct
 * {@code kestra.logs.h2.url}, the log store writes/reads a separate H2 database (created by the
 * {@code 2.0.14-logs-h2} migration), and the primary database receives none of those logs.
 */
@MicronautTest
@Property(name = "kestra.logs.type", value = "h2")
@Property(name = "kestra.logs.h2.url", value = "jdbc:h2:mem:logs_dedicated;TIME ZONE=UTC;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=TRUE")
// Use an isolated primary datasource so this test's migration history (which records the log-table
// migrations applied to the dedicated log database) does not leak into the shared in-memory "public"
// database used by the other repository tests in the same JVM.
@Property(name = "datasources.h2.url", value = "jdbc:h2:mem:logs_dedicated_primary;LOCK_TIMEOUT=30000;TIME ZONE=UTC;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class H2LogDataStoreDedicatedTest {

    @Inject
    LogDataStoreInterfaceFactory logRepositoryInterfaceFactory;

    // The low-level repository bound to the PRIMARY datasource's `logs` table, to prove logs did NOT land there.
    @Inject
    @Named("logs")
    H2Repository<LogEntry> primaryLogs;

    @Test
    void shouldWriteAndReadFromDedicatedDatabaseAndNotThePrimary() {
        // Given: the h2 log store built against the dedicated datasource
        LogDataStoreInterface dedicated = logRepositoryInterfaceFactory.make("h2", java.util.Map.of());
        String executionId = "dedicated-exec-" + Instant.now().toEpochMilli();
        LogEntry log = LogEntry.builder()
            .tenantId("main")
            .namespace("io.kestra.tests")
            .flowId("dedicated-flow")
            .executionId(executionId)
            .level(Level.INFO)
            .timestamp(Instant.now())
            .message("hello dedicated log store")
            .build();

        // When: a log is saved through the dedicated store
        dedicated.save(log);

        // Then: it is readable from the dedicated store...
        List<LogEntry> fromDedicated = dedicated.findByExecutionId("main", executionId, Level.TRACE);
        assertThat(fromDedicated).hasSize(1);
        assertThat(fromDedicated.getFirst().getMessage()).isEqualTo("hello dedicated log store");

        // ...and absent from the primary database.
        assertThat(primaryLogs.count(DSL.field(DSL.name("execution_id")).eq(executionId))).isZero();
    }
}
