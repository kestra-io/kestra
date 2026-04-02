package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.Kestra;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "flow",
    description = "Manage flows",
    mixinStandardHelpOptions = true,
    subcommands = {
        FlowTestCommand.class,
        FlowDotCommand.class,
        FlowDeleteCommand.class,
        FlowExportCommand.class,
        FlowsSyncFromSourceCommand.class
    }
)
@Slf4j
public class FlowCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        return Kestra.runCli(new String[] { "flow", "--help" });
    }
}
