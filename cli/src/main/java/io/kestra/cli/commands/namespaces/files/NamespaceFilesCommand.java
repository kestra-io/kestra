package io.kestra.cli.commands.namespaces.files;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "files",
    description = "Manage namespace files",
    mixinStandardHelpOptions = true,
    subcommands = {
        NamespaceFilesUpdateCommand.class,
    }
)
@Slf4j
public class NamespaceFilesCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "namespace", "files",  "--help");

        return 0;
    }
}
