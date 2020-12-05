package org.kestra.cli.commands.flows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.serializers.JacksonMapper;
import org.kestra.core.serializers.YamlFlowParser;
import picocli.CommandLine;

import java.nio.file.Path;
import javax.inject.Inject;
import javax.validation.ConstraintViolationException;

@CommandLine.Command(
    name = "validate",
    description = "validate a flow"
)
public class ValidateCommand extends AbstractCommand {
    private static final ObjectMapper mapper = JacksonMapper.ofYaml();

    @Inject
    private YamlFlowParser yamlFlowParser;

    @CommandLine.Parameters(index = "0", description = "the flow file to test")
    private Path file;

    public ValidateCommand() {
        super(false);
    }

    @Override
    public Integer call() throws Exception {
        super.call();

        try {
            Flow parse = yamlFlowParser.parse(file.toFile());
            stdOut(mapper.writeValueAsString(parse));
        } catch (ConstraintViolationException e) {
            ValidateCommand.handleException(e);

            return 1;
        }

        return 0;
    }

    public static void handleException(ConstraintViolationException e) {
        stdErr("@|fg(red) Unable to parse flow due to the following error(s):|@");
        e.getConstraintViolations()
            .forEach(constraintViolation -> {
                stdErr(
                    "- {0} at @|underline,blue {1}|@ with value @|bold,yellow {2}|@ ",
                    constraintViolation.getMessage(),
                    constraintViolation.getPropertyPath(),
                    constraintViolation.getInvalidValue()
                );
            });
    }
}
