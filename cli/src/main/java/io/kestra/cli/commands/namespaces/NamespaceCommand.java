package io.kestra.cli.commands.namespaces;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import io.kestra.cli.commands.namespaces.files.NamespaceFilesCommand;
import io.kestra.cli.commands.namespaces.kv.KvCommand;
import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "namespace",
    description = "Manage namespaces",
    mixinStandardHelpOptions = true,
    subcommands = {
        NamespaceFilesCommand.class,
        KvCommand.class
    }
)
@Slf4j
public class NamespaceCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "namespace", "--help");

        return 0;
    }
}
