package io.kestra.cli.commands.sys.database;

import io.kestra.cli.AbstractCommand;
import picocli.CommandLine;

@CommandLine.Command(
    name = "database",
    description = "manage Kestra database",
    mixinStandardHelpOptions = true,
    subcommands = {
        DatabaseMigrateCommand.class,
    }
)
public class DatabaseCommand extends AbstractCommand {
}
