package io.kestra.cli.commands.servers;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.kestra.cli.Kestra;
import io.kestra.core.migration.MigrationStartupRunner;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies what {@code server worker} must <em>not</em> pull in, given that deployments commonly
 * share one configuration across every server type: a worker owns no repository and talks to the
 * rest of the cluster over gRPC, so a {@code datasources} or {@code kestra.repository.type} block
 * inherited from that shared configuration must not make it touch the database.
 *
 * <p>
 * Only the negative side is asserted here — the positive side (non-worker servers do migrate and do
 * get a datasource) is covered by every test that boots a JDBC-backed server, all of which would
 * fail on an empty schema.
 */
class WorkerServerContextTest {

    private static ApplicationContext workerContext(String... args) {
        ApplicationContext ctx = Kestra.applicationContext(
            Kestra.class,
            new String[] { Environment.CLI, Environment.TEST },
            args
        );
        ctx.start();
        return ctx;
    }

    @Test
    void shouldNotRegisterTheMigrationStartupTriggerOnWorker() {
        try (ApplicationContext ctx = workerContext("server", "worker")) {
            assertThat(ctx.containsBean(MigrationStartupRunner.class)).isFalse();
        }
    }

    @Test
    void shouldNotRegisterADatasourceOnWorker() {
        // Given the test configuration declares a `datasources.h2` block, as a shared configuration would.
        try (ApplicationContext ctx = workerContext("server", "worker")) {
            assertThat(ctx.getEnvironment().containsProperty("datasources.h2.url")).isTrue();

            // Then no pool was built from it.
            assertThat(ctx.containsBean(DataSource.class)).isFalse();
        }
    }

    @Test
    void shouldStartWorkerWhenTheConfiguredDatabaseIsUnreachable(@TempDir Path tempDir) throws Exception {
        // Given a shared configuration pointing at a database the worker has no route to.
        Path config = Files.writeString(tempDir.resolve("kestra.yml"), """
            datasources:
              postgres:
                url: jdbc:postgresql://unreachable.invalid:5432/kestra
                driverClassName: org.postgresql.Driver
                username: kestra
                password: k3str4
            """);

        // When/Then the worker starts instead of dying on `Failed to initialize pool`.
        assertThatCode(() ->
        {
            try (ApplicationContext ctx = workerContext("server", "worker", "-c", config.toString())) {
                assertThat(ctx.isRunning()).isTrue();
            }
        }).doesNotThrowAnyException();
    }
}
