package io.kestra.cli.commands.configs.sys;
import io.kestra.cli.commands.HelloCommand;
import io.kestra.cli.commands.flows.FlowCreateCommand;
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

class NoConfigCommandTest {

    @Test
    void shouldSuccessWithHelloCommandWithoutConfig() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        try (ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).start()) {
            int exitCode=PicocliRunner.call(HelloCommand.class, ctx);

            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("Hello from kestra");
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

            int exitCode = PicocliRunner.call(FlowCreateCommand.class, ctx, createArgs);


            assertThat(exitCode).isNotZero();
            assertThat(out.toString()).isEmpty();
            assertThat(err.toString()).contains("No bean of type [io.kestra.core.repositories.FlowRepositoryInterface] exists");
        }
    }

}
