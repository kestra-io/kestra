package io.kestra.cli.commands.configs.sys;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import io.kestra.cli.ConsoleCapture;
import io.kestra.cli.commands.flows.FlowDeleteCommand;
import io.kestra.cli.commands.namespaces.kv.KvCommand;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies CLI behavior without repository configuration:
 * - Repo-independent commands succeed (e.g. KV with no params).
 * - Repo-dependent commands fail with a clear error.
 */
class NoConfigCommandTest {

    @Test
    void shouldSucceedWithNamespaceKVCommandWithoutParamsAndConfig() {
        try (ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).start()) {
            String[] args = {};
            var result = ConsoleCapture.run(() -> PicocliRunner.call(KvCommand.class, ctx, args));

            assertThat(result.value()).isZero();
            assertThat(result.out()).contains("Usage: kestra namespace kv");
        }
    }

    @Test
    void shouldFailWithDeleteFlowCommandWithoutConfig() throws URISyntaxException {
        URL flowUrl = NoConfigCommandTest.class.getClassLoader().getResource("crudFlow/date.yml");
        Objects.requireNonNull(flowUrl, "Test flow resource not found");

        try (
            ApplicationContext ctx = ApplicationContext.builder()
                .deduceEnvironment(false)
                .start()
        ) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] createArgs = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "namespace",
                "id"
            };

            var result = ConsoleCapture.run(() -> PicocliRunner.call(FlowDeleteCommand.class, ctx, createArgs));

            assertThat(result.value()).isNotZero();
            // check that the only log is an access log: this has the advantage to also check that access log is working!
            assertThat(result.out()).contains("DELETE /api/v1/main/flows/namespace/id HTTP/1.1 | status: 500");
            assertThat(result.err()).contains("No bean of type [io.kestra.core.repositories.FlowRepositoryInterface] exists");
        }
    }

}
