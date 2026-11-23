package io.kestra.cli.commands.flows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.kestra.cli.Kestra;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTestCommandTest {
    @Test
    void shouldRunTheFlowOnAnEphemeralDatabaseRatherThanTheConfiguredOne(@TempDir Path tempDir) throws Exception {
        // Given a configuration describing a database this command must not use — and could not
        // use, since nothing listens on that port. Keyed 'h2' to override the datasource the test
        // environment declares: a second key would produce a second datasource, which no Kestra
        // deployment supports.
        Path config = Files.writeString(tempDir.resolve("kestra.yml"), """
            datasources:
              h2:
                url: jdbc:postgresql://127.0.0.1:1/kestra
                driverClassName: org.postgresql.Driver
                username: kestra
                password: k3str4
            kestra:
              repository:
                type: postgres
              queue:
                type: postgres
            """);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        int exitCode;
        try {
            // When
            exitCode = Kestra.runCli(new String[] {
                "flow", "test", "src/test/resources/flows/same/first.yaml", "-c", config.toString()
            });
        } finally {
            System.setOut(originalOut);
        }

        // Then the flow ran, which it only can on a database of its own.
        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("Successfully executed the flow with execution");
        assertThat(out.toString()).contains("SUCCESS");
    }
}
