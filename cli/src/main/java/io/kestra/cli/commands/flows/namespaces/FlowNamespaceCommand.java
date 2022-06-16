package io.kestra.cli.commands.flows.namespaces;

import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import picocli.CommandLine;

@CommandLine.Command(
    name = "namespace",
    description = "handle namespace flows",
    mixinStandardHelpOptions = true,
    subcommands = {
        FlowNamespaceUpdateCommand.class,
    }
)
@Slf4j
public class FlowNamespaceCommand extends AbstractCommand {

}
