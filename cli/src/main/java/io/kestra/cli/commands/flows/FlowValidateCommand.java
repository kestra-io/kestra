package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractValidateCommand;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.services.FlowService;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(
    name = "validate",
    description = "validate a flow"
)
public class FlowValidateCommand extends AbstractValidateCommand {
    @Inject
    private YamlFlowParser yamlFlowParser;

    @Inject
    private ModelValidator modelValidator;

    @Inject
    private FlowService flowService;

    @Override
    public Integer call() throws Exception {
        return this.call(
            Flow.class,
            yamlFlowParser,
            modelValidator,
            (Object object) -> {
                Flow flow = (Flow) object;
                return flow.getNamespace() + " / " + flow.getId();
            },
            (Object object) -> {
                Flow flow = (Flow) object;
                List<String> warnings = new ArrayList<>();
                warnings.addAll(flowService.deprecationPaths(flow).stream().map(deprecation -> deprecation + " is deprecated").toList());
                warnings.addAll(flowService.warnings(flow));
                return warnings;
            }
        );
    }
}
