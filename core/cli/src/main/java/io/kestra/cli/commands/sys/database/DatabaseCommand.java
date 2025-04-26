package io.kestra.cli.commands.sys.database;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import picocli.CommandLine;

@CommandLine.Command(
    name = "database",
    description = "Manage Kestra database",
    mixinStandardHelpOptions = true,
    subcommands = {
        DatabaseMigrateCommand.class,
    }
)
public class DatabaseCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "sys", "database", "--help");

        return 0;
    }
}
