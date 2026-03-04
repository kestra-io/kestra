package io.kestra.cli.commands.configs.sys;
import io.kestra.cli.commands.flows.FlowCreateCommand;
import io.kestra.cli.commands.namespaces.kv.KvCommand;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * Verifies CLI behavior without repository configuration:
 * - Repo-independent commands succeed (e.g. KV with no params).
 * - Repo-dependent commands fail with a clear error.
 */
class NoConfigCommandTest {

    @Test
    void shouldSucceedWithNamespaceKVCommandWithoutParamsAndConfig() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).start()) {
            String[] args = {};
            Integer call = PicocliRunner.call(KvCommand.class, ctx, args);

            assertThat(call).isZero();
            assertThat(out.toString()).contains("Usage: kestra namespace kv");
        }
    }

    @Test
    void shouldFailWithCreateFlowCommandWithoutConfig() throws URISyntaxException {
        URL flowUrl = NoConfigCommandTest.class.getClassLoader().getResource("crudFlow/date.yml");
        Objects.requireNonNull(flowUrl, "Test flow resource not found");

        Path flowPath = Paths.get(flowUrl.toURI());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err=new ByteArrayOutputStream();

        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));

        try (ApplicationContext ctx = ApplicationContext.builder()
            .deduceEnvironment(false)
            .start()) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] createArgs = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                flowPath.toString(),
            };

            Integer exitCode = PicocliRunner.call(FlowCreateCommand.class, ctx, createArgs);


            assertThat(exitCode).isNotZero();
            // check that the only log is an access log: this has the advantage to also check that access log is working!
            assertThat(out.toString()).contains("POST /api/v1/main/flows HTTP/1.1 | status: 500");
            assertThat(err.toString()).contains("No bean of type [io.kestra.core.repositories.FlowRepositoryInterface] exists");
        }
    }

}
