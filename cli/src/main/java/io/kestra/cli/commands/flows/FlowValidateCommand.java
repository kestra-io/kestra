package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractValidateCommand;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlParser;
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
    private YamlParser yamlParser;

    @Inject
    private ModelValidator modelValidator;

    @Inject
    private FlowService flowService;

    @Override
    public Integer call() throws Exception {
        return this.call(
            Flow.class,
            yamlParser,
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
            },
            (Object object) -> {
                Flow flow = (Flow) object;
                return flowService.relocations(flow.generateSource()).stream().map(relocation -> relocation.from() + " is replaced by " + relocation.to()).toList();
            }
        );
    }
}
