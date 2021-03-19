package io.kestra.cli.commands.flows;

import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import io.kestra.cli.commands.flows.namespaces.FlowNamespaceCommand;
import picocli.CommandLine;

@CommandLine.Command(
    name = "flow",
    description = "handle flows",
    mixinStandardHelpOptions = true,
    subcommands = {
        ValidateCommand.class,
        FlowTestCommand.class,
        FlowNamespaceCommand.class,
        FlowDotCommand.class
    }
)
@Slf4j
public class FlowCommand extends AbstractCommand {
    public FlowCommand() {
        super(false);
    }

    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "flow",  "--help");

        return 0;
    }
}
