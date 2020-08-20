package org.kestra.cli.commands.flows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kestra.cli.AbstractCommand;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.serializers.JacksonMapper;
import org.kestra.core.serializers.YamlFlowParser;
import picocli.CommandLine;

import java.io.IOException;
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
            System.out.println(mapper.writeValueAsString(parse));
        } catch (ConstraintViolationException e) {
            System.err.println("Unable to parse flow due to the following error(s):");
            e.getConstraintViolations()
                .forEach(constraintViolation -> {
                    System.err.println("- " + constraintViolation.getMessage() + " with value '" + constraintViolation.getInvalidValue() + "'");
                });
            return 1;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return 0;
    }
}
