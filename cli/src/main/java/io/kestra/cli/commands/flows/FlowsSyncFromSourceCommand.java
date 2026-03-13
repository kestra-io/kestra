package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractApiCommand;
import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.repositories.FlowRepositoryInterface;
import jakarta.inject.Inject;
import java.util.ArrayList;
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
    private TenantIdSelectorService tenantService;

    @SuppressWarnings("deprecation")
    @Override
    public Integer call() throws Exception {
        super.call();

        FlowRepositoryInterface repository = applicationContext.getBean(FlowRepositoryInterface.class);
        String tenant = tenantService.getTenantId(tenantId);

        List<FlowWithSource> persistedFlows = repository.findAllWithSource(tenant);

        int count = 0;
        List<String> flowsInError = new ArrayList<>();
        for (FlowWithSource persistedFlow : persistedFlows) {
            try {
                // Ensure exactly one trailing newline. We need this new line
                // because when we update a flow from its source,
                // we don't update it if no change is detected.
                // The goal here is to force an update from the source for every flows
                GenericFlow flow = GenericFlow.fromYaml(tenant,persistedFlow.getSource() + System.lineSeparator());
                repository.update(flow, persistedFlow);
                stdOut("- %s.%s".formatted(flow.getNamespace(), flow.getId()));
                count++;
            } catch (RuntimeException e){
                String flowInError = persistedFlow.getNamespace() + "." + persistedFlow.getId();
                stdErr("Unable to update flow %s".formatted(flowInError), e.getMessage());
                flowsInError.add(flowInError);
            }
        }

        stdOut("%s flow(s) successfully updated!".formatted(count));

        if (!flowsInError.isEmpty()) {
            flowsInError.forEach(flowId -> stdErr("Flow %s hasn't been updated".formatted(flowId)));
            return 1;
        }

        return 0;
    }

    protected boolean loadExternalPlugins() {
        return true;
    }


}
