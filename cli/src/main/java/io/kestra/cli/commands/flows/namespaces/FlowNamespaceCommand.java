package io.kestra.cli.commands.flows.namespaces;

import io.kestra.cli.App;
import io.micronaut.configuration.picocli.PicocliRunner;
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

        PicocliRunner.call(App.class, "flow", "namespace",  "--help");

        return 0;
    }
}
