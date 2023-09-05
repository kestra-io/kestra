package io.kestra.cli.commands.templates.namespaces;

import io.kestra.cli.commands.AbstractServiceNamespaceUpdateCommand;
import io.kestra.cli.commands.templates.TemplateValidateCommand;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.templates.TemplateEnabled;
import io.kestra.core.serializers.YamlFlowParser;
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
import java.util.stream.Collectors;
import javax.validation.ConstraintViolationException;

@CommandLine.Command(
    name = "update",
    description = "handle namespace templates",
    mixinStandardHelpOptions = true
)
@Slf4j
@TemplateEnabled
public class TemplateNamespaceUpdateCommand extends AbstractServiceNamespaceUpdateCommand {
    @Inject
    public YamlFlowParser yamlFlowParser;

    @Override
    public Integer call() throws Exception {
        super.call();

        try (var files = Files.walk(directory)) {
            List<Template> templates = files
                .filter(Files::isRegularFile)
                .filter(YamlFlowParser::isValidExtension)
                .map(path -> yamlFlowParser.parse(path.toFile(), Template.class))
                .collect(Collectors.toList());

            if (templates.isEmpty()) {
                stdOut("No template found on '{}'", directory.toFile().getAbsolutePath());
            }

            try (DefaultHttpClient client = client()) {
                MutableHttpRequest<List<Template>> request = HttpRequest
                    .POST("/api/v1/templates/" + namespace + "?delete=" + delete, templates);

                List<UpdateResult> updated = client.toBlocking().retrieve(
                    this.requestOptions(request),
                    Argument.listOf(UpdateResult.class)
                );

                stdOut(updated.size() + " template(s) for namespace '" + namespace + "' successfully updated !");
                updated.forEach(template -> stdOut("- " + template.getNamespace() + "." + template.getId()));
            } catch (HttpClientResponseException e) {
                TemplateValidateCommand.handleHttpException(e, "template");

                return 1;
            }
        } catch (ConstraintViolationException e) {
            TemplateValidateCommand.handleException(e, "template");

            return 1;
        }

        return 0;
    }
}
