package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractApiCommand;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.YamlParser;
import jakarta.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

@CommandLine.Command(
    name = "syncFromSource",
    description = "Update a single flow",
    mixinStandardHelpOptions = true
)
@Slf4j
public class FlowsSyncFromSourceCommand extends AbstractApiCommand {

    @Inject
    private YamlParser yamlParser;

    @SuppressWarnings("deprecation")
    @Override
    public Integer call() throws Exception {
        super.call();

        FlowRepositoryInterface repository = applicationContext.getBean(FlowRepositoryInterface.class);

        List<FlowWithSource> persistedFlows = repository.findAllWithSource(tenantId);

        int count = 0;
        for (FlowWithSource persistedFlow : persistedFlows) {
            // Ensure exactly one trailing newline. We need this new line
            // because when we update a flow from its source,
            // we don't update it if no change is detected.
            // The goal here is to force an update from the source for every flows
            String source = persistedFlow.getSource() + System.lineSeparator();
            FlowWithSource flow = FlowWithSource.of(yamlParser.parse(source, Flow.class), source);
            repository.update(flow, persistedFlow, source, flow);
            stdOut("- %s.%s".formatted(flow.getNamespace(), flow.getId()));
            count++;
        }
        stdOut("%s flow(s) successfully updated!".formatted(count));

        return 0;
    }

    protected boolean loadExternalPlugins() {
        return true;
    }


}
