package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.Graph2DotService;
import io.kestra.core.utils.GraphUtils;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command(
    name = "dot",
    description = "Generate a DOT graph from a file"
)
@Slf4j
public class FlowDotCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Parameters(index = "0", description = "The flow file to display")
    private Path file;

    @Override
    public Integer call() throws Exception {
        super.call();

        YamlParser parser = applicationContext.getBean(YamlParser.class);
        Flow flow = parser.parse(file.toFile(), Flow.class);

        GraphCluster graph = GraphUtils.of(flow, null);

        stdOut(Graph2DotService.dot(graph.getGraph()));

        return 0;
    }
}
