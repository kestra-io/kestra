package io.kestra.cli.commands.namespaces.files;

import io.kestra.cli.AbstractCommand;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "files",
    description = "handle namespace files",
    mixinStandardHelpOptions = true,
    subcommands = {
        NamespaceFilesUpdateCommand.class,
    }
)
@Slf4j
public class NamespaceFilesCommand extends AbstractCommand {

}
