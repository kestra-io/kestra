package io.kestra.cli.commands.plugins;

import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import picocli.CommandLine;

@CommandLine.Command(
    name = "plugins",
    description = "Manage plugins",
    mixinStandardHelpOptions = true,
    subcommands = {
        PluginInstallCommand.class,
        PluginListCommand.class,
        PluginDocCommand.class
    }
)
@Slf4j
public class PluginCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "plugins",  "--help");

        return 0;
    }
}
