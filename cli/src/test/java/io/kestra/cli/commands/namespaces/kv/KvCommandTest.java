package io.kestra.cli.commands.namespaces.kv;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import java.util.Map;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class KvCommandTest {
    @Test
    void runWithNoParam() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.builder()
            .deduceEnvironment(false)
            .properties(Map.of(
                "kestra.repository.type", "memory",
                "kestra.queue.type", "memory"
            ))
            .start()) {
            String[] args = {};
            Integer call = PicocliRunner.call(KvCommand.class, ctx, args);

            assertThat(call).isZero();
            assertThat(out.toString()).contains("Usage: kestra namespace kv");
        }
    }
}
