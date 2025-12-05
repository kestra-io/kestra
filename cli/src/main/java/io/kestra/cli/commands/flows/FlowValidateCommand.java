package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractValidateCommand;
import io.kestra.cli.services.TenantIdSelectorService;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.services.FlowValidationService;
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
    private FlowValidationService flowValidationService;

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
                warnings.addAll(flowValidationService.deprecationPaths(flow).stream().map(deprecation -> deprecation + " is deprecated").toList());
                warnings.addAll(flowValidationService.warnings(flow, tenantIdSelectorService.getTenantIdAndAllowEETenants(tenantId)));
                return warnings;
            },
            (Object object) -> {
                FlowWithSource flow = (FlowWithSource) object;
                return flowValidationService.relocations(flow.sourceOrGenerateIfNull()).stream().map(relocation -> relocation.from() + " is replaced by " + relocation.to()).toList();
            }
        );
    }
}
