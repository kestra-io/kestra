package io.kestra.cli.commands.sys.statestore;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import picocli.CommandLine;

@CommandLine.Command(
    name = "state-store",
    description = "Manage Kestra State Store",
    mixinStandardHelpOptions = true,
    subcommands = {
        StateStoreMigrateCommand.class,
    }
)
public class StateStoreCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "sys", "state-store", "--help");

        return 0;
    }
}
