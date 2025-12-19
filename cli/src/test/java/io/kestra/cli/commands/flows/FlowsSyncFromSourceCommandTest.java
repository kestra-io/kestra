package io.kestra.cli.commands.flows;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.server.EmbeddedServer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlowsSyncFromSourceCommandTest {
    @Test
    void updateAllFlowsFromSource()  {
        URL directory = FlowUpdatesCommandTest.class.getClassLoader().getResource("flows");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));
        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {

            EmbeddedServer embeddedServer = ctx.getBean(EmbeddedServer.class);
            embeddedServer.start();

            String[] args = {
                "--plugins",
                "/tmp", // pass this arg because it can cause failure
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word",
                "--delete",
                directory.getPath(),
            };
            PicocliRunner.call(FlowUpdatesCommand.class, ctx, args);

            assertThat(out.toString()).contains("successfully updated !");
            out.reset();

            FlowRepositoryInterface repository = ctx.getBean(FlowRepositoryInterface.class);
            List<Flow> flows = repository.findAll(MAIN_TENANT);
            for (Flow flow : flows) {
                assertThat(flow.getRevision()).isEqualTo(1);
            }

            args = new String[]{
                "--plugins",
                "/tmp", // pass this arg because it can cause failure
                "--server",
                embeddedServer.getURL().toString(),
                "--user",
                "myuser:pass:word"

            };
            PicocliRunner.call(FlowsSyncFromSourceCommand.class, ctx, args);

            assertThat(out.toString()).contains("4 flow(s) successfully updated!");
            assertThat(out.toString()).contains("- io.kestra.outsider.quattro");
            assertThat(out.toString()).contains("- io.kestra.cli.second");
            assertThat(out.toString()).contains("- io.kestra.cli.third");
            assertThat(out.toString()).contains("- io.kestra.cli.first");

            flows = repository.findAll(MAIN_TENANT);
            for (Flow flow : flows) {
                assertThat(flow.getRevision()).isEqualTo(2);
            }
        }
    }
}
