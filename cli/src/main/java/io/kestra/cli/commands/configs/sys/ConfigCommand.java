package io.kestra.cli.commands.configs.sys;

import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import picocli.CommandLine;

@CommandLine.Command(
    name = "configs",
    description = "Manage configuration",
    mixinStandardHelpOptions = true,
    subcommands = {
        ConfigPropertiesCommand.class,
    }
)
@Slf4j
public class ConfigCommand extends AbstractCommand {
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "configs",  "--help");

        return 0;
    }
}
