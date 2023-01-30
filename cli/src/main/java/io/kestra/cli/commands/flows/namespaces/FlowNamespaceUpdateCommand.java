package io.kestra.cli.commands.flows.namespaces;

import io.kestra.cli.commands.AbstractServiceNamespaceUpdateCommand;
import io.kestra.cli.commands.flows.ValidateCommand;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.serializers.YamlFlowParser;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.netty.DefaultHttpClient;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

@CommandLine.Command(
    name = "update",
    description = "handle namespace flows",
    mixinStandardHelpOptions = true
)
@Slf4j
public class FlowNamespaceUpdateCommand extends AbstractServiceNamespaceUpdateCommand {
    @Inject
    public YamlFlowParser yamlFlowParser;


    @Override
    public Integer call() throws Exception {
        super.call();

        List<String> flows = Files.walk(directory)
            .filter(Files::isRegularFile)
            .filter(YamlFlowParser::isValidExtension)
            .map(path -> {
                try {
                    return Files.readString(path, Charset.defaultCharset());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toList());

        String body = "";
        if (flows.size() == 0) {
            stdOut("No flow found on '{}'", directory.toFile().getAbsolutePath());
        } else {
            body = String.join("\n---\n", flows);
        }
        try(DefaultHttpClient client = client()) {
            MutableHttpRequest<String> request = HttpRequest
                .POST("/api/v1/flows/" + namespace, body).contentType(MediaType.APPLICATION_YAML);

            List<FlowWithSource> updated = client.toBlocking().retrieve(
                this.requestOptions(request),
                Argument.listOf(FlowWithSource.class)
            );

            stdOut(updated.size() + " flow(s) for namespace '" + namespace + "' successfully updated !");
            updated.forEach(flow -> stdOut("- " + flow.getNamespace() + "."  + flow.getId()));
        } catch (HttpClientResponseException e){
            ValidateCommand.handleHttpException(e);
            return 1;
        }

        return 0;
    }
}
