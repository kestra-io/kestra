package io.kestra.cli.commands.flows.namespaces;

import io.kestra.cli.AbstractApiCommand;
import io.kestra.cli.commands.flows.ValidateCommand;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.serializers.YamlFlowParser;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.netty.DefaultHttpClient;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolationException;

@CommandLine.Command(
    name = "update",
    description = "handle namespace flows",
    mixinStandardHelpOptions = true
)
@Slf4j
public class FlowNamespaceUpdateCommand extends AbstractApiCommand {
    @Inject
    private YamlFlowParser yamlFlowParser;

    @CommandLine.Parameters(index = "0", description = "the namespace of flow to update")
    private String namespace;

    @CommandLine.Parameters(index = "1", description = "the directory containing flows to from current namespace")
    private Path directory;

    @Override
    public Integer call() throws Exception {
        super.call();

        try {
            List<Flow> flows = Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(YamlFlowParser::isValidExtension)
                .map(path -> yamlFlowParser.parse(path.toFile()))
                .collect(Collectors.toList());

            if (flows.size() == 0) {
                stdErr("No flow found on '{}'", directory.toFile().getAbsolutePath());
                return 1;
            }

            try(DefaultHttpClient client = client()) {
                MutableHttpRequest<List<Flow>> request = HttpRequest
                    .POST("/api/v1/flows/" + namespace, flows);

                List<Flow> updated = client.toBlocking().retrieve(
                    this.requestOptions(request),
                    Argument.listOf(Flow.class)
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
