package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractValidateCommand;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlFlowParser;
import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(
    name = "validate",
    description = "validate a flow"
)
public class FlowValidateCommand extends AbstractValidateCommand {
    @Inject
    private YamlFlowParser yamlFlowParser;

    @Inject
    private ModelValidator modelValidator;

    @Override
    public Integer call() throws Exception {
        return this.call(
            Flow.class,
            yamlFlowParser,
            modelValidator,
            (Object object) -> {
                Flow flow = (Flow) object;
                return flow.getNamespace() + " / " + flow.getId();
            }
        );
    }
}
