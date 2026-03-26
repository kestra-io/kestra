package io.kestra.cli.commands.migrations.metadata;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import io.kestra.cli.App;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

public class SecretsMetadataMigrationCommandTest {
    @Test
    void run() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] secretMetadataMigrationCommand = {
                "migrate", "metadata", "secrets"
            };
            PicocliRunner.call(App.class, ctx, secretMetadataMigrationCommand);

            assertThat(err.toString()).contains("❌ Secrets Metadata migration failed: Secret migration is not needed in the OSS version");
        }
    }
}
