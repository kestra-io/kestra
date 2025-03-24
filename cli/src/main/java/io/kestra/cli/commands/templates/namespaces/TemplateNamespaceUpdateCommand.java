package io.kestra.cli.commands.templates.namespaces;

import io.kestra.cli.AbstractValidateCommand;
import io.kestra.cli.commands.AbstractServiceNamespaceUpdateCommand;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.templates.TemplateEnabled;
import io.kestra.core.serializers.YamlParser;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.netty.DefaultHttpClient;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.nio.file.Files;
import java.util.List;

import jakarta.validation.ConstraintViolationException;

@CommandLine.Command(
    name = "update",
    description = "Update namespace templates",
    mixinStandardHelpOptions = true
)
@Slf4j
@TemplateEnabled
public class TemplateNamespaceUpdateCommand extends AbstractServiceNamespaceUpdateCommand {
    @Inject
    public YamlParser yamlParser;

    @Override
    public Integer call() throws Exception {
        super.call();

        try (var files = Files.walk(directory)) {
            List<Template> templates = files
                .filter(Files::isRegularFile)
                .filter(YamlParser::isValidExtension)
                .map(path -> yamlParser.parse(path.toFile(), Template.class))
                .toList();

            if (templates.isEmpty()) {
                stdOut("No template found on '{}'", directory.toFile().getAbsolutePath());
            }

            try (DefaultHttpClient client = client()) {
                MutableHttpRequest<List<Template>> request = HttpRequest
                    .POST(apiUri("/templates/") + namespace + "?delete=" + delete, templates);

                List<UpdateResult> updated = client.toBlocking().retrieve(
                    this.requestOptions(request),
                    Argument.listOf(UpdateResult.class)
                );

                stdOut(updated.size() + " template(s) for namespace '" + namespace + "' successfully updated !");
                updated.forEach(template -> stdOut("- " + template.getNamespace() + "." + template.getId()));
            } catch (HttpClientResponseException e) {
                AbstractValidateCommand.handleHttpException(e, "template");

                return 1;
            }
        } catch (ConstraintViolationException e) {
            AbstractValidateCommand.handleException(e, "template");

            return 1;
        }

        return 0;
    }
}
