package io.kestra.cli.commands.sys;

import io.kestra.cli.commands.sys.database.DatabaseCommand;
import io.kestra.cli.commands.sys.statestore.StateStoreCommand;
import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import picocli.CommandLine;

@CommandLine.Command(
    name = "sys",
    description = "Manage system maintenance mode",
    mixinStandardHelpOptions = true,
    subcommands = {
        ReindexCommand.class,
        DatabaseCommand.class,
        SubmitQueuedCommand.class,
        StateStoreCommand.class
    }
)
@Slf4j
public class SysCommand extends AbstractCommand {
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "sys",  "--help");

        return 0;
    }
}
