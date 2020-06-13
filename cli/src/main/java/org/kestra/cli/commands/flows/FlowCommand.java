package org.kestra.cli.commands.flows;

import lombok.extern.slf4j.Slf4j;
import org.kestra.cli.AbstractCommand;
import org.kestra.cli.commands.servers.*;
import picocli.CommandLine;

@CommandLine.Command(
    name = "flow",
    description = "handle flows",
    mixinStandardHelpOptions = true,
    subcommands = {
        ValidateCommand.class,
    }
)
@Slf4j
public class FlowCommand extends AbstractCommand {
    public FlowCommand() {
        super(false);
    }
}
