package io.kestra.cli.commands.sys.database;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseCommandTest {
    @Test
    void runWithNoParam() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).start()) {
            String[] args = {};
            Integer call = PicocliRunner.call(DatabaseCommand.class, ctx, args);

            assertThat(call).isZero();
            assertThat(out.toString()).contains("Usage: kestra sys database");
        }
    }
}
