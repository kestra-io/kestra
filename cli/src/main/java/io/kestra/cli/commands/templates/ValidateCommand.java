package io.kestra.cli.commands.templates;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.cli.AbstractValidateCommand;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlFlowParser;
import jakarta.inject.Inject;
import picocli.CommandLine;

import javax.validation.ConstraintViolationException;
import java.nio.file.Path;

@CommandLine.Command(
    name = "validate",
    description = "validate a template"
)
public class ValidateCommand extends AbstractValidateCommand {
    private static final ObjectMapper mapper = JacksonMapper.ofYaml();

    @Inject
    private YamlFlowParser yamlFlowParser;

    @Inject
    private ModelValidator modelValidator;

    @CommandLine.Parameters(index = "0", description = "the template file to test")
    private Path file;

    @Override
    public Integer call() throws Exception {
        super.call();

        try {
            Template parse = yamlFlowParser.parseTemplate(file.toFile());
            modelValidator.validate(parse);
            stdOut(mapper.writeValueAsString(parse));
        } catch (ConstraintViolationException e) {
            ValidateCommand.handleException(e, "template");

            return 1;
        }

        return 0;
    }
}
