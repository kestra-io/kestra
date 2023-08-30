package io.kestra.cli.commands.templates.namespaces;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.templates.TemplateEnabled;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "namespace",
    description = "handle namespace templates",
    mixinStandardHelpOptions = true,
    subcommands = {
        TemplateNamespaceUpdateCommand.class,
    }
)
@Slf4j
@TemplateEnabled
public class TemplateNamespaceCommand extends AbstractCommand {

}
