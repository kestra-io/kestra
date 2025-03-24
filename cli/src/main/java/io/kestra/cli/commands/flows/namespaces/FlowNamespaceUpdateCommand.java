package io.kestra.cli.commands.flows.namespaces;

import io.kestra.cli.AbstractValidateCommand;
import io.kestra.cli.commands.AbstractServiceNamespaceUpdateCommand;
import io.kestra.cli.commands.flows.IncludeHelperExpander;
import io.kestra.core.serializers.YamlParser;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.netty.DefaultHttpClient;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

@CommandLine.Command(
    name = "update",
    description = "Update flows in namespace",
    mixinStandardHelpOptions = true
)
@Slf4j
public class FlowNamespaceUpdateCommand extends AbstractServiceNamespaceUpdateCommand {
    @Inject
    public YamlParser yamlParser;

    @CommandLine.Option(names = {"--override-namespaces"}, negatable = true, description = "Replace namespace of all flows by the one provided")
    public boolean override = false;

    @SuppressWarnings("deprecation")
    @Override
    public Integer call() throws Exception {
        super.call();

        try (var files = Files.walk(directory)) {
            List<String> flows = files
                .filter(Files::isRegularFile)
                .filter(YamlParser::isValidExtension)
                .map(path -> {
                    try {
                        return IncludeHelperExpander.expand(Files.readString(path, Charset.defaultCharset()), path.getParent());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

            String body = "";
            if (flows.isEmpty()) {
                stdOut("No flow found on '{}'", directory.toFile().getAbsolutePath());
            } else {
                body = String.join("\n---\n", flows);
            }
            if (override) {
                body = body.replaceAll("(?m)^namespace:.+", "namespace: " + namespace);
            }
            try(DefaultHttpClient client = client()) {
                MutableHttpRequest<String> request = HttpRequest
                    .POST(apiUri("/flows/") + namespace + "?delete=" + delete, body).contentType(MediaType.APPLICATION_YAML);

                List<UpdateResult> updated = client.toBlocking().retrieve(
                    this.requestOptions(request),
                    Argument.listOf(UpdateResult.class)
                );

                stdOut(updated.size() + " flow(s) for namespace '" + namespace + "' successfully updated !");
                updated.forEach(flow -> stdOut("- " + flow.getNamespace() + "."  + flow.getId()));
            } catch (HttpClientResponseException e){
                AbstractValidateCommand.handleHttpException(e, "flow");
                return 1;
            }
        } catch (ConstraintViolationException e) {
            AbstractValidateCommand.handleException(e, "flow");

            return 1;
        }

        return 0;
    }
}
