package io.kestra.repository.mysql;

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
 * Proves the dedicated-datasource path on real MySQL: with {@code kestra.logs.type=mysql} + a
 * distinct {@code kestra.logs.mysql.url} (a separate database), the log store writes/reads that
 * database (created by the {@code 0-init-logs-mysql} migration), and the primary database receives
 * none of those logs.
 */
@Tag("integration")
@MicronautTest
@Property(name = "kestra.logs.type", value = "mysql")
@Property(name = "kestra.logs.mysql.url", value = "jdbc:mysql://localhost:3306/logs_dedicated")
@Property(name = "kestra.logs.mysql.username", value = "kestra")
@Property(name = "kestra.logs.mysql.password", value = "k3str4")
class MysqlLogStoreDedicatedTest {

    @Inject
    LogRepositoryInterfaceFactory logRepositoryInterfaceFactory;

    @Inject
    @Named("logs")
    MysqlRepository<LogEntry> primaryLogs;

    @Test
    void shouldWriteAndReadFromDedicatedDatabaseAndNotThePrimary() {
        LogRepositoryInterface dedicated = logRepositoryInterfaceFactory.make("mysql", Map.of());
        String executionId = "dedicated-exec-" + Instant.now().toEpochMilli();
        LogEntry log = LogEntry.builder()
            .tenantId("main")
            .namespace("io.kestra.tests")
            .flowId("dedicated-flow")
            .executionId(executionId)
            .level(Level.INFO)
            .timestamp(Instant.now())
            .message("hello dedicated mysql log store")
            .build();

        dedicated.save(log);

        List<LogEntry> fromDedicated = dedicated.findByExecutionId("main", executionId, Level.TRACE);
        assertThat(fromDedicated).hasSize(1);
        assertThat(fromDedicated.getFirst().getMessage()).isEqualTo("hello dedicated mysql log store");

        assertThat(primaryLogs.count(DSL.field(DSL.name("execution_id")).eq(executionId))).isZero();
    }
}
