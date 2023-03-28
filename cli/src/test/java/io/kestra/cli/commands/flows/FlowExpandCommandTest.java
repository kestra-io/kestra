package io.kestra.cli.commands.flows;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;

class FlowExpandCommandTest {
    @Test
    void run() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] args = {
                "src/test/resources/helper/flow.yaml"
            };
            Integer call = PicocliRunner.call(FlowExpandCommand.class, ctx, args);

            assertThat(call, is(0));
            assertThat(out.toString(), is(
                "id: include\n" +
                    "namespace: io.kestra.cli\n" +
                    "\n" +
                    "# The list of tasks\n" +
                    "tasks:\n" +
                    "- id: t1\n" +
                    "  type: io.kestra.core.tasks.debugs.Return\n" +
                    "  format: \"Lorem ipsum dolor sit amet\"\n" +
                    "- id: t2\n" +
                    "  type: io.kestra.core.tasks.debugs.Return\n" +
                    "  format: |\n" +
                    "    Lorem ipsum dolor sit amet\n" +
                    "    Lorem ipsum dolor sit amet\n"
            ));
        }
    }
}