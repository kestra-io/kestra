package io.kestra.cli.commands.templates;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import io.kestra.cli.commands.templates.namespaces.TemplateNamespaceCommand;
import io.kestra.core.models.templates.TemplateEnabled;
import io.micronaut.configuration.picocli.PicocliRunner;
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

        PicocliRunner.call(App.class, "template",  "--help");

        return 0;
    }
}
