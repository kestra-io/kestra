package io.kestra.cli.commands.servers;

import org.junit.jupiter.api.Test;

import io.kestra.cli.Kestra;
import io.kestra.core.migration.MigrationStartupRunner;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static ApplicationContext workerContext() {
        ApplicationContext ctx = Kestra.applicationContext(
            Kestra.class,
            new String[] { Environment.CLI, Environment.TEST },
            "server", "worker"
        );
        ctx.start();
        return ctx;
    }

    @Test
    void shouldNotRegisterTheMigrationStartupTriggerOnWorker() {
        try (ApplicationContext ctx = workerContext()) {
            assertThat(ctx.containsBean(MigrationStartupRunner.class)).isFalse();
        }
    }
}
