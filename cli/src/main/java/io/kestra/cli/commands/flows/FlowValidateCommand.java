package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractValidateCommand;
import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.services.FlowService;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(
    name = "validate",
    description = "Validate a flow"
)
public class FlowValidateCommand extends AbstractValidateCommand {

    @Inject
    private ModelValidator modelValidator;

    @Inject
    private FlowService flowService;

    @Inject
    private TenantIdSelectorService tenantIdSelectorService;


    @Override
    public Integer call() throws Exception {
        return this.call(
            FlowWithSource.class,
            modelValidator,
            (Object object) -> {
                FlowWithSource flow = (FlowWithSource) object;
                return flow.getNamespace() + " / " + flow.getId();
            },
            (Object object) -> {
                FlowWithSource flow = (FlowWithSource) object;
                List<String> warnings = new ArrayList<>();
                warnings.addAll(flowService.deprecationPaths(flow).stream().map(deprecation -> deprecation + " is deprecated").toList());
                warnings.addAll(flowService.warnings(flow, tenantIdSelectorService.getTenantIdAndAllowEETenants(tenantId)));
                return warnings;
            },
            (Object object) -> {
                FlowWithSource flow = (FlowWithSource) object;
                return flowService.relocations(flow.sourceOrGenerateIfNull()).stream().map(relocation -> relocation.from() + " is replaced by " + relocation.to()).toList();
            }
        );
    }
}
