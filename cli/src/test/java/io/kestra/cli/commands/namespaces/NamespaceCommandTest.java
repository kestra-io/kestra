package io.kestra.cli.commands.namespaces;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.cli.ConsoleCapture;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class NamespaceCommandTest {
    @Test
    void runWithNoParam() {
        try (
            ApplicationContext ctx = ApplicationContext.builder()
                .deduceEnvironment(false)
                .properties(
                    Map.of(
                        "kestra.repository.type", "memory",
                        "kestra.queue.type", "memory"
                    )
                )
                .start()
        ) {
            String[] args = {};
            var result = ConsoleCapture.run(() -> PicocliRunner.call(NamespaceCommand.class, ctx, args));

            assertThat(result.value()).isZero();
            assertThat(result.out()).contains("Usage: kestra namespace");
        }
    }
}
