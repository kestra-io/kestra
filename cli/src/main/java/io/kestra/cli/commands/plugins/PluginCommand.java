package io.kestra.cli.commands.plugins;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import picocli.CommandLine.Command;

@Command(
    name = "plugins",
    description = "Manage plugins",
    mixinStandardHelpOptions = true,
    subcommands = {
        PluginInstallCommand.class,
        PluginListCommand.class,
        PluginDocCommand.class,
        PluginSearchCommand.class
    }
)
public class PluginCommand extends AbstractCommand {

    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "plugins", "--help");

        return 0;
    }

    @Override
    protected boolean loadExternalPlugins() {
        return false;
    }
}
