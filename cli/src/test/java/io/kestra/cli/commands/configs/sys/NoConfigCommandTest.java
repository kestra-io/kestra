package io.kestra.cli.commands.configs.sys;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import io.kestra.cli.commands.namespaces.kv.KvCommand;

import java.util.Map;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies CLI behavior without full backend configuration.
 */
class NoConfigCommandTest {

    @Test
    void shouldSucceedWithNamespaceKVCommandWithoutParamsAndConfig() {
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
