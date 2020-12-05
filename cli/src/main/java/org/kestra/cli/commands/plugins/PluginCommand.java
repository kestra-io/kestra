package org.kestra.cli.commands.plugins;

import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.cli.App;
import picocli.CommandLine;

@CommandLine.Command(
    name = "plugins",
    description = "handle plugins",
    mixinStandardHelpOptions = true,
    subcommands = {
        PluginInstallCommand.class,
        PluginListCommand.class,
        PluginDocCommand.class
    }
)
@Slf4j
public class PluginCommand extends AbstractCommand {
    public PluginCommand() {
        super(false);
    }

    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "plugins",  "--help");

        return 0;
    }
}
