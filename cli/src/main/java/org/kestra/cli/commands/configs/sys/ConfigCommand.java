package org.kestra.cli.commands.configs.sys;

import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.cli.App;
import picocli.CommandLine;

@CommandLine.Command(
    name = "configs",
    description = "handle configs",
    mixinStandardHelpOptions = true,
    subcommands = {
        ConfigPropertiesCommand.class,
    }
)
@Slf4j
public class ConfigCommand extends AbstractCommand {
    public ConfigCommand() {
        super(false);
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "configs",  "--help");

        return 0;
    }
}
