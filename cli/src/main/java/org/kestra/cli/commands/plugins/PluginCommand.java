package org.kestra.cli.commands.plugins;

import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import picocli.CommandLine;

@CommandLine.Command(
    name = "plugins",
    description = "handle plugins",
    mixinStandardHelpOptions = true,
    subcommands = {
        PluginInstallCommand.class
    }
)
@Slf4j
public class PluginCommand extends AbstractCommand {
    public PluginCommand() {
        super(false);
    }
}
