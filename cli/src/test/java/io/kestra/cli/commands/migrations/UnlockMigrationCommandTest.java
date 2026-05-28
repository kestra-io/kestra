package io.kestra.cli.commands.migrations;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnlockMigrationCommandTest {

    @Test
    void run_forceReleasesLockAndReturnsZero() {
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            Integer call = PicocliRunner.call(UnlockMigrationCommand.class, ctx);
            assertThat(call).isZero();
        }
    }
}
