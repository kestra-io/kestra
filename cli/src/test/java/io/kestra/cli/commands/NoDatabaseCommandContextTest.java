package io.kestra.cli.commands;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.kestra.cli.Kestra;
import io.kestra.core.migration.MigrationStartupRunner;
import io.kestra.core.repositories.FlowRepositoryInterface;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies what a {@link NoDatabaseCommandInterface} command must <em>not</em> pull in. Deployments
 * commonly share one configuration across everything, so a command that owns no repository has to
 * work against a configuration describing a database it has no route to.
 */
class NoDatabaseCommandContextTest {
    /**
     * Shaped like a configuration shared across server types: a repository type, which is what
     * keeps the repositories registered, and a server type, which is what registers the server
     * facets gated on it. The datasource is keyed {@code h2} to override the one the test
     * environment declares rather than add a second — two would fail for that reason instead of the
     * unreachable host.
     */
    private static Path unreachableDatabaseConfig(Path tempDir) throws Exception {
        return Files.writeString(tempDir.resolve("kestra.yml"), """
            datasources:
              h2:
                url: jdbc:postgresql://unreachable.invalid:5432/kestra
                driverClassName: org.postgresql.Driver
                username: kestra
                password: k3str4
            kestra:
              server-type: STANDALONE
              repository:
                type: postgres
              queue:
                type: postgres
            """);
    }

    private static ApplicationContext context(String... args) {
        ApplicationContext ctx = Kestra.applicationContext(
            Kestra.class,
            new String[] { Environment.CLI, Environment.TEST },
            args
        );
        ctx.start();
        return ctx;
    }

    @Test
    void shouldStartWithoutTheConfiguredDatabase(@TempDir Path tempDir) throws Exception {
        Path config = unreachableDatabaseConfig(tempDir);

        try (ApplicationContext ctx = context("flow", "dot", "src/test/resources/flows/same/first.yaml", "-c", config.toString())) {
            // Given the configuration was read, as a shared configuration would be.
            assertThat(ctx.getEnvironment().containsProperty("datasources.h2.url")).isTrue();
            assertThat(ctx.getEnvironment().getProperty("kestra.repository.type", String.class)).contains("postgres");

            // Then no pool was built from it, and nothing set out to migrate its schema. Both
            // assertions also pin the package prefixes NoDatabaseApplicationContext filters on.
            assertThat(ctx.containsBean(DataSource.class)).isFalse();
            assertThat(ctx.containsBean(MigrationStartupRunner.class)).isFalse();

            // And no repository either: @RepositoryBean carries a @Requires(property =
            // "kestra.server-type", ...) stereotype, which requiresServerType() also drops. Any
            // bean that depends on a repository being present must be dropped here too, rather than
            // fail on a repository this context never built.
            assertThat(ctx.containsBean(FlowRepositoryInterface.class)).isFalse();
        }
    }

    @Test
    void shouldStartTheRootCommandWithoutADatasource() {
        // `docker run kestra/kestra` lands on the root command, which only prints usage. It is not
        // an AbstractCommand, so it never reads --config, but a shared configuration still reaches
        // it through MICRONAUT_CONFIG_FILES.
        try (ApplicationContext ctx = context()) {
            assertThat(ctx.containsBean(DataSource.class)).isFalse();
        }
    }

    @Test
    void shouldKeepTheDatasourceWhenTheArgumentsDoNotParse() {
        // A parse failure reports the root as the leaf, and the root owns no repository — but
        // picocli goes on to instantiate the command the arguments named, which may. Enterprise
        // Edition's `flow delete` resolves a tenant through one.
        try (ApplicationContext ctx = context("flow", "delete")) {
            assertThat(ctx.containsBean(DataSource.class)).isTrue();
        }
    }

    @Test
    void shouldStillBuildADatasourceForACommandThatOwnsARepository(@TempDir Path tempDir) throws Exception {
        // Empty, so the database comes from the test configuration rather than from a developer's
        // own ~/.kestra/config.yml, which this one has to be able to reach.
        Path config = Files.writeString(tempDir.resolve("empty.yml"), "");

        // The flavour is opt-in, so a command that reads a repository keeps its datasource.
        try (ApplicationContext ctx = context("sys", "reindex", "-c", config.toString())) {
            assertThat(ctx.containsBean(DataSource.class)).isTrue();
        }
    }

    @Test
    void shouldRunTheCommandWithoutTheConfiguredDatabase(@TempDir Path tempDir) throws Exception {
        Path config = unreachableDatabaseConfig(tempDir);

        // Through the real entrypoint, which activates only the CLI environment, so there is no
        // in-memory datasource from the test configuration to fall back on.
        // --plugins so AbstractCommand.maybeInitPlugins() resolves the registry and the plugin
        // manager, the path every command shares and the one a deployment always takes.
        int exitCode = Kestra.runCli(new String[] {
            "flow", "dot", "src/test/resources/flows/same/first.yaml",
            "--plugins", tempDir.toString(), "-c", config.toString()
        });

        assertThat(exitCode).isZero();
    }
}
