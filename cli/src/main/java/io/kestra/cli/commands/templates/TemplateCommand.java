package io.kestra.cli.commands.templates;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import io.kestra.cli.commands.templates.namespaces.TemplateNamespaceCommand;
import io.kestra.core.models.templates.TemplateEnabled;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "template",
    description = "Manage templates",
    mixinStandardHelpOptions = true,
    subcommands = {
        TemplateNamespaceCommand.class,
        TemplateValidateCommand.class,
        TemplateExportCommand.class,
    }
)
@Slf4j
@TemplateEnabled
public class TemplateCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        return App.runCli(new String[]{"template",  "--help"});
    }
}
