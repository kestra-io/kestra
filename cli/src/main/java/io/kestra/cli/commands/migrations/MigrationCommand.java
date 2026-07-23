package io.kestra.cli.commands.migrations;

import io.kestra.cli.Kestra;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "migrate",
    description = "handle migrations",
    mixinStandardHelpOptions = true,
    subcommands = {
        RunMigrationCommand.class,
        PlanMigrationCommand.class,
        RepairMigrationCommand.class,
        UnlockMigrationCommand.class
    }
)
@Slf4j
public class MigrationCommand extends AbstractMigrationCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        return Kestra.runCli(new String[] { "migrate", "--help" });
    }
}
