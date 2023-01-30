package io.kestra.cli.commands.flows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.cli.AbstractValidateCommand;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlFlowParser;
import picocli.CommandLine;

import java.nio.file.Path;
import jakarta.inject.Inject;
import javax.validation.ConstraintViolationException;

@CommandLine.Command(
    name = "validate",
    description = "validate a flow"
)
public class ValidateCommand extends AbstractValidateCommand {
    private static final ObjectMapper mapper = JacksonMapper.ofYaml();

    @Inject
    private YamlFlowParser yamlFlowParser;

    @Inject
    private ModelValidator modelValidator;

    @CommandLine.Parameters(index = "0", description = "the flow file to test")
    private Path file;

    @Override
    public Integer call() throws Exception {
        super.call();

        try {
            Flow parse = yamlFlowParser.parse(file.toFile());
            modelValidator.validate(parse);
            stdOut(mapper.writeValueAsString(parse));
        } catch (ConstraintViolationException e) {
            ValidateCommand.handleException(e, "flow");

            return 1;
        }

        return 0;
    }
}
