package io.kestra.cli.commands.migrations;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RepairMigrationCommandTest {

    @Test
    void run_repairsAppliedMigrationChecksum() {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            Integer call = PicocliRunner.call(RepairMigrationCommand.class, ctx, "2.0.01-upgrade");
            assertThat(call).isZero();
        }
    }
}
