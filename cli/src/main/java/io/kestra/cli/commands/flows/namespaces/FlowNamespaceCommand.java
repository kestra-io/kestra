package io.kestra.cli.commands.flows.namespaces;

import io.kestra.cli.App;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import picocli.CommandLine;

@CommandLine.Command(
    name = "namespace",
    description = "Manage namespace flows",
    mixinStandardHelpOptions = true,
    subcommands = {
        FlowNamespaceUpdateCommand.class,
    }
)
@Slf4j
public class FlowNamespaceCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        return App.runCli(new String[]{"flow", "namespace",  "--help"});
    }
}
