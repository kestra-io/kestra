package io.kestra.cli.commands.templates;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import io.kestra.cli.commands.templates.namespaces.TemplateNamespaceCommand;
import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "template",
    description = "handle templates",
    mixinStandardHelpOptions = true,
    subcommands = {
        TemplateNamespaceCommand.class,
        TemplateValidateCommand.class
    }
)
@Slf4j
public class TemplateCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "template",  "--help");

        return 0;
    }
}
