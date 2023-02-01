package io.kestra.cli.commands.flows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.cli.AbstractValidateCommand;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlFlowParser;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.netty.DefaultHttpClient;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Override
    public Integer call() throws Exception {
        super.call();
        AtomicInteger returnCode = new AtomicInteger(0);

        if(this.local) {
            Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(YamlFlowParser::isValidExtension)
                .forEach(path -> {
                    try {
                        Flow parse = yamlFlowParser.parse(path.toFile());
                        modelValidator.validate(parse);
                        stdOut("@|green \u2713|@ - " + parse.getId());
                    } catch (ConstraintViolationException e) {
                        stdErr("@|red \u2718|@ - " + path);
                        ValidateCommand.handleException(e, "flow");
                        returnCode.set(1);
                    }
                });
        } else {
            String body = ValidateCommand.buildYamlBody(directory);

            try(DefaultHttpClient client = client()) {
                MutableHttpRequest<String> request = HttpRequest
                    .POST("/api/v1/flows/validate", body).contentType(MediaType.APPLICATION_YAML);

                List<ValidateConstraintViolation> validations = client.toBlocking().retrieve(
                    this.requestOptions(request),
                    Argument.listOf(ValidateConstraintViolation.class)
                );
                validations.forEach(
                    validation -> {
                        if (validation.getConstraints() == null){
                            stdOut("@|green \u2713|@ - " + validation.getIdentity());
                        } else {
                            try {
                                stdErr("@|red \u2718|@ - " + validation.getIdentity(directory));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            ValidateCommand.handleValidateConstraintViolation(validation, "flow");
                            returnCode.set(1);
                        }
                    }
                );
            } catch (HttpClientResponseException e){
                ValidateCommand.handleHttpException(e, "flow");

                return 1;
            }
        }

        return returnCode.get();
    }
}
