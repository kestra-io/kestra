package io.kestra.cli.commands.flows;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class SingleFlowCommandsTest {

    @Test
    void all() {
        URL flow = SingleFlowCommandsTest.class.getClassLoader().getResource("crudFlow/date.yml");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] createArgs = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                flow.getPath(),
            };
            PicocliRunner.call(FlowCreateCommand.class, ctx, createArgs);

            assertThat(out.toString()).contains("Flow successfully created !");

            out.reset();

            String[] updateArgs = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                flow.getPath(),
                "io.kestra.cli",
                "date"
            };
            PicocliRunner.call(FlowUpdateCommand.class, ctx, updateArgs);

            assertThat(out.toString()).contains("Flow successfully updated !");

            out.reset();

            String[] deleteArgs = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "io.kestra.cli",
                "date"
            };
            PicocliRunner.call(FlowDeleteCommand.class, ctx, deleteArgs);

            assertThat(out.toString()).contains("Flow successfully deleted !");
        }
    }
}
