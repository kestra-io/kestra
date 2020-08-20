package org.kestra.cli.commands.flows.namespaces;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.DefaultHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.kestra.cli.AbstractApiCommand;
import org.kestra.cli.commands.flows.ValidateCommand;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.serializers.YamlFlowParser;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.validation.ConstraintViolationException;

import static org.kestra.core.utils.Rethrow.throwFunction;

@CommandLine.Command(
    name = "update",
    description = "handle namespace flows",
    mixinStandardHelpOptions = true,
    subcommands = {
        ValidateCommand.class,
    }
)
@Slf4j
public class FlowNamespaceUpdateCommand extends AbstractApiCommand {
    @Inject
    private YamlFlowParser yamlFlowParser;

    @CommandLine.Parameters(index = "0", description = "the namespace of flow to update")
    private String namespace;

    @CommandLine.Parameters(index = "1", description = "the directory containing flows to from current namespace")
    private Path directory;

    public FlowNamespaceUpdateCommand() {
        super(false);
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        try {
            List<Flow> flows = Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(path -> FilenameUtils.getExtension(path.toFile().getAbsolutePath()).equals("yaml"))
                .map(throwFunction(path -> yamlFlowParser.parse(path.toFile())))
                .collect(Collectors.toList());

            if (flows.size() == 0) {
                System.err.println("No flow found on '" + directory.toFile().getAbsolutePath() + "'");
                return 1;
            }

            try(DefaultHttpClient client = client()) {
                MutableHttpRequest<List<Flow>> request = HttpRequest
                    .POST("/api/v1/flows/" + namespace, flows);

                List<Flow> updated = client.toBlocking().retrieve(
                    this.requestOptions(request),
                    Argument.listOf(Flow.class)
                );

                System.out.println(updated.size() + " flow(s) for namespace '" + namespace + "' successfully updated !");
                updated.forEach(flow -> System.out.println("- " + flow.getNamespace() + "."  + flow.getId()));
            }
        } catch (ConstraintViolationException e) {
            System.err.println("Unable to parse flow due to the following error(s):");
            e.getConstraintViolations()
                .forEach(constraintViolation -> {
                    System.err.println("- " + constraintViolation.getMessage() + " with value '" + constraintViolation.getInvalidValue() + "'");
                });
            return 1;
        }

        return 0;
    }
}
