package io.kestra.cli.commands.plugins;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import lombok.SneakyThrows;
import picocli.CommandLine.Command;

@Command(
    name = "plugins",
    description = "Manage plugins",
    mixinStandardHelpOptions = true,
    subcommands = {
        PluginInstallCommand.class,
        PluginUninstallCommand.class,
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

        return App.runCli(new String[]{"plugins", "--help"});
    }

    @Override
    protected boolean loadExternalPlugins() {
        return false;
    }
}
