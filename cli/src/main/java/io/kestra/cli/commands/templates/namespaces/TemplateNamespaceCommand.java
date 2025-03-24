package io.kestra.cli.commands.templates.namespaces;

import io.kestra.cli.AbstractCommand;
import io.kestra.cli.App;
import io.kestra.core.models.templates.TemplateEnabled;
import io.micronaut.configuration.picocli.PicocliRunner;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "namespace",
    description = "Manage namespace templates",
    mixinStandardHelpOptions = true,
    subcommands = {
        TemplateNamespaceUpdateCommand.class,
    }
)
@Slf4j
@TemplateEnabled
public class TemplateNamespaceCommand extends AbstractCommand {
    @SneakyThrows
    @Override
    public Integer call() throws Exception {
        super.call();

        PicocliRunner.call(App.class, "template", "namespace", "--help");

        return 0;
    }
}
