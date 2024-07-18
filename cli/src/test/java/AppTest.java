import io.kestra.cli.commands.servers.AbstractServerCommand;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;
import io.kestra.cli.App;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AppTest {
    @Test
    public void testHelp() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            PicocliRunner.call(App.class, ctx, "--help");

            assertThat(out.toString(), containsString("kestra"));
        }
    }

    @Test
    public void testSeverCommandValidation() {
        assertThrows(AbstractServerCommand.ServerCommandException.class, () -> App.main(new String[]{"server", "webserver"}));
    }
}
