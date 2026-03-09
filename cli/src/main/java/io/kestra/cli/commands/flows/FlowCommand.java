package io.kestra.cli.commands.flows;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import io.kestra.cli.commands.flows.namespaces.FlowNamespaceCommand;
import picocli.CommandLine;

@CommandLine.Command(
    name = "flow",
    description = "Manage flows",
    mixinStandardHelpOptions = true,
    subcommands = {
        FlowValidateCommand.class,
        FlowTestCommand.class,
        FlowNamespaceCommand.class,
        FlowDotCommand.class,
        FlowExportCommand.class,
        FlowUpdateCommand.class,
        FlowUpdatesCommand.class,
        FlowsSyncFromSourceCommand.class
    }
)
@Slf4j
public class FlowCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        return App.runCli(new String[]{"flow",  "--help"});
    }
}
