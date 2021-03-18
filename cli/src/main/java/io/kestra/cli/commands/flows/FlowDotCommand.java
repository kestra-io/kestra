package io.kestra.cli.commands.flows;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.hierarchies.FlowGraph;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.services.Graph2DotService;
import io.kestra.core.services.GraphService;
import picocli.CommandLine;

import java.nio.file.Path;
import java.security.SecureRandom;
import javax.inject.Inject;

@CommandLine.Command(
    name = "dot",
    description = "generate a dot graph from a file"
)
@Slf4j
@Prototype
public class FlowDotCommand extends AbstractCommand {
    @CommandLine.Parameters(index = "0", description = "the flow file to display")
    private Path file;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    private ApplicationContext applicationContext;

    private static final SecureRandom random = new SecureRandom();

    public FlowDotCommand() {
        super(false);
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        YamlFlowParser parser = applicationContext.getBean(YamlFlowParser.class);
        Flow flow = parser.parse(file.toFile());
        GraphCluster graph = new GraphCluster();

        GraphService.sequential(
            graph,
            flow.getTasks(),
            flow.getErrors(),
            null,
            null
        );

        stdOut(Graph2DotService.dot(graph.getGraph()));

        return 0;
    }
}
