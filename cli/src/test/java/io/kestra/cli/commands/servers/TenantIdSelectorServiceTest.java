package io.kestra.cli.commands.servers;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import io.kestra.cli.App;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

public class TenantIdSelectorServiceTest {

    @Test
    void should_fail_without_tenant_id() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] start = {
                "server", "standalone",
                "-f", "unused",
                "--tenant", "wrong_tenant"
            };
            PicocliRunner.call(App.class, ctx, start);

            assertThat(err.toString()).contains("Tenant id can only be 'main'");
            err.reset();
        }
    }

}
