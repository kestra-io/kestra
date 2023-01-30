package io.kestra.cli.commands.templates.namespaces;

import io.kestra.cli.commands.AbstractServiceNamespaceUpdateCommand;
import io.kestra.cli.commands.flows.ValidateCommand;
import io.kestra.core.models.templates.Template;
import io.kestra.core.serializers.YamlFlowParser;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.netty.DefaultHttpClient;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import javax.validation.ConstraintViolationException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

@CommandLine.Command(
    name = "update",
    description = "handle namespace flows",
    mixinStandardHelpOptions = true
)
@Slf4j
public class TemplateNamespaceUpdateCommand extends AbstractServiceNamespaceUpdateCommand {
    @Inject
    public YamlFlowParser yamlFlowParser;

    @Override
    public Integer call() throws Exception {
        super.call();

        try {
            List<Template> templates = Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(YamlFlowParser::isValidExtension)
                .map(path -> yamlFlowParser.parseTemplate(path.toFile()))
                .collect(Collectors.toList());

            if (templates.size() == 0) {
                stdErr("No flow found on '{}'", directory.toFile().getAbsolutePath());
                return 1;
            }

            try(DefaultHttpClient client = client()) {
                MutableHttpRequest<List<Template>> request = HttpRequest
                    .POST("/api/v1/templates/" + namespace, templates   );

                List<Template> updated = client.toBlocking().retrieve(
                    this.requestOptions(request),
                    Argument.listOf(Template.class)
                );

                stdOut(updated.size() + " template(s) for namespace '" + namespace + "' successfully updated !");
                updated.forEach(template -> stdOut("- " + template.getNamespace() + "."  + template.getId()));
            }
        } catch (ConstraintViolationException e) {
            ValidateCommand.handleException(e);

            return 1;
        }

        return 0;
    }
}
