package io.kestra.cli.commands.migrations;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.Kestra;
import io.kestra.cli.commands.migrations.metadata.MetadataMigrationCommand;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "migrate",
    description = "handle migrations",
    mixinStandardHelpOptions = true,
    subcommands = {
        MetadataMigrationCommand.class,
        V2TriggerMigrationCommand.class,
        V2ExecutionResubmitMigrationCommand.class,
        V2ExecutionOutputMigrationCommand.class
    }
)
@Slf4j
public class MigrationCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        return Kestra.runCli(new String[] { "migrate", "--help" });
    }
}
