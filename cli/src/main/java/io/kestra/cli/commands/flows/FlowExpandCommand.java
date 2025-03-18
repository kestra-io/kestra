package io.kestra.cli.commands.flows;

import io.kestra.cli.AbstractCommand;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlParser;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

@CommandLine.Command(
    name = "expand",
    description = "Deprecated - expand a flow"
)
@Deprecated
public class FlowExpandCommand extends AbstractCommand {

    @CommandLine.Parameters(index = "0", description = "The flow file to expand")
    private Path file;

    @Inject
    private YamlParser yamlParser;

    @Inject
    private ModelValidator modelValidator;

    @Override
    public Integer call() throws Exception {
        super.call();
        stdErr("Warning, this functionality is deprecated and will be removed at some point.");
        String content = IncludeHelperExpander.expand(Files.readString(file), file.getParent());
        Flow flow = yamlParser.parse(content, Flow.class);
        modelValidator.validate(flow);
        stdOut(content);
        return 0;
    }
}
