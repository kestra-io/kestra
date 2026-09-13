package io.kestra.cli.commands.configs.sys;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.Kestra;
import io.kestra.cli.commands.NoDatabaseCommandInterface;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "configs",
    description = "Manage configuration",
    mixinStandardHelpOptions = true,
    subcommands = {
        ConfigPropertiesCommand.class,
        ConfigValidateCommand.class,
    }
)
@Slf4j
public class ConfigCommand extends AbstractCommand implements NoDatabaseCommandInterface {
    @Override
    public Integer call() throws Exception {
        super.call();

        return Kestra.runCli(new String[] { "configs", "--help" });
    }
}
