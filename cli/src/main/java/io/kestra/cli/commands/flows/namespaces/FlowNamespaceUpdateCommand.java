package io.kestra.cli.commands.flows.namespaces;

import io.kestra.cli.commands.AbstractServiceNamespaceUpdateCommand;
import io.kestra.cli.commands.flows.ValidateCommand;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.serializers.YamlFlowParser;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.netty.DefaultHttpClient;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolationException;

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

        try {
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

            if (flows.size() == 0) {
                stdErr("No flow found on '{}'", directory.toFile().getAbsolutePath());
                return 1;
            }
            try(DefaultHttpClient client = client()) {
                MutableHttpRequest<String> request = HttpRequest
                    .POST("/api/v1/flows/" + namespace, String.join("\n---\n", flows)).contentType(MediaType.APPLICATION_YAML);

                List<FlowWithSource> updated = client.toBlocking().retrieve(
                    this.requestOptions(request),
                    Argument.listOf(FlowWithSource.class)
                );

                stdOut(updated.size() + " flow(s) for namespace '" + namespace + "' successfully updated !");
                updated.forEach(flow -> stdOut("- " + flow.getNamespace() + "."  + flow.getId()));
            }
        } catch (ConstraintViolationException e) {
            ValidateCommand.handleException(e);

            return 1;
        }

        return 0;
    }
}
