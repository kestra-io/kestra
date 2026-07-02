package io.kestra.cli.commands.migrations;

import io.kestra.core.migration.MigrationScript;
import io.kestra.jdbc.migration.JdbcMigrationHistoryStore;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RepairMigrationCommandTest {

    @Test
    void run_repairsAppliedMigrationChecksum() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            JdbcMigrationHistoryStore historyStore = ctx.getBean(JdbcMigrationHistoryStore.class);

            historyStore.bootstrapIfNeeded();
            if (!historyStore.isApplied("2.0.01-upgrade")) {
                historyStore.markApplied(testScript("2.0.01-upgrade", "current-checksum"), 0L);
            }
            historyStore.updateChecksum(testScript("2.0.01-upgrade", "original-checksum"));

            Integer call = PicocliRunner.call(RepairMigrationCommand.class, ctx, "2.0.01-upgrade");
            assertThat(call).isZero();
        }
    }

    private static MigrationScript testScript(final String scriptId, final String checksum) {
        return new MigrationScript() {
            @Override
            public String scriptId() {
                return scriptId;
            }

            @Override
            public String description() {
                return "OSS H2 upgrade: apply Kestra 2.0 schema changes on Flyway-managed databases";
            }

            @Override
            public String checksum() {
                return checksum;
            }

            @Override
            public void migrate() {
            }
        };
    }
}
