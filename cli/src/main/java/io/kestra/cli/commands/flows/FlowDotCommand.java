package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.services.Graph2DotService;
import io.kestra.core.services.GraphService;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command(
    name = "dot",
    description = "generate a dot graph from a file"
)
@Slf4j
public class FlowDotCommand extends AbstractCommand {
    @Inject
    private ApplicationContext applicationContext;

    @CommandLine.Parameters(index = "0", description = "the flow file to display")
    private Path file;

    @Override
    public Integer call() throws Exception {
        super.call();

        YamlFlowParser parser = applicationContext.getBean(YamlFlowParser.class);
        Flow flow = parser.parse(file.toFile(), Flow.class);

        GraphCluster graph = GraphService.of(flow, null);

        stdOut(Graph2DotService.dot(graph.getGraph()));

        return 0;
    }
}
