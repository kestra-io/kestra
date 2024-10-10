package io.kestra.cli.commands.flows;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

public class SingleFlowCommandsTest {


    @Test
    void all() {
        URL flow = SingleFlowCommandsTest.class.getClassLoader().getResource("flows/quattro.yml");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] deleteArgs = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "io.kestra.outsider",
                "quattro"
            };
            PicocliRunner.call(FlowDeleteCommand.class, ctx, deleteArgs);

            assertThat(out.toString(), containsString("Flow successfully deleted !"));
            out.reset();

            String[] createArgs = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                flow.getPath(),
            };
            PicocliRunner.call(FlowCreateCommand.class, ctx, createArgs);

            assertThat(out.toString(), containsString("Flow successfully created !"));


            out.reset();String[] updateArgs = {
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                flow.getPath(),
                "io.kestra.outsider",
                "quattro"
            };
            PicocliRunner.call(FlowUpdateCommand.class, ctx, updateArgs);

            assertThat(out.toString(), containsString("Flow successfully updated !"));
            out.reset();
        }
    }

}
