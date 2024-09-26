package io.kestra.cli;

import io.kestra.cli.commands.flows.FlowValidateCommand;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.serializers.YamlFlowParser;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.netty.DefaultHttpClient;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolationException;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

public abstract class AbstractValidateCommand extends AbstractApiCommand {
    @CommandLine.Option(names = {"--local"}, description = "If validation should be done locally or using a remote server", defaultValue = "false")
    protected boolean local;

    @CommandLine.Parameters(index = "0", description = "the directory containing files to check")
    protected Path directory;

    public static void handleException(ConstraintViolationException e, String resource) {
        stdErr("\t@|fg(red) Unable to parse {0} due to the following error(s):|@", resource);
        e.getConstraintViolations()
            .forEach(constraintViolation -> {
                stdErr(
                    "\t- @|bold,yellow {0} : {1} |@",
                    constraintViolation.getMessage().replace("\n"," - "),
                    constraintViolation.getPropertyPath()
                );
            });
    }

    public static void handleHttpException(HttpClientResponseException e, String resource) {
        stdErr("\t@|fg(red) Unable to parse {0}s due to the following error:|@", resource);
        stdErr(
            "\t- @|bold,yellow {0}|@",
            e.getMessage()
        );
    }

    public static void handleValidateConstraintViolation(ValidateConstraintViolation validateConstraintViolation, String resource){
        stdErr("\t@|fg(red) Unable to parse {0}s due to the following error:|@", resource);
        stdErr(
            "\t- @|bold,yellow {0}|@",
            validateConstraintViolation.getConstraints()
        );
    }

    public static String buildYamlBody(Path directory) throws IOException {
        try(var files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                .filter(YamlFlowParser::isValidExtension)
                .map(throwFunction(path -> Files.readString(path, Charset.defaultCharset())))
                .collect(Collectors.joining("\n---\n"));
        }
    }

    // bug in micronaut, we can't inject YamlFlowParser & ModelValidator, so we inject from implementation
    public Integer call(
        Class<?> cls,
        YamlFlowParser yamlFlowParser,
        ModelValidator modelValidator,
        Function<Object, String> identity,
        Function<Object, List<String>> warningsFunction
    ) throws Exception {
        super.call();

        AtomicInteger returnCode = new AtomicInteger(0);
        String clsName = cls.getSimpleName().toLowerCase();

        if(this.local) {
            try(var files = Files.walk(directory)) {
                files.filter(Files::isRegularFile)
                    .filter(YamlFlowParser::isValidExtension)
                    .forEach(path -> {
                        try {
                            Object parse = yamlFlowParser.parse(path.toFile(), cls);
                            modelValidator.validate(parse);
                            stdOut("@|green \u2713|@ - " + identity.apply(parse));
                            List<String> warnings = warningsFunction.apply(parse);
                            warnings.forEach(warning -> stdOut("@|bold,yellow \u26A0|@ - " + warning));
                        } catch (ConstraintViolationException e) {
                            stdErr("@|red \u2718|@ - " + path);
                            AbstractValidateCommand.handleException(e, clsName);
                            returnCode.set(1);
                        }
                    });
            }
        } else {
            String body = AbstractValidateCommand.buildYamlBody(directory);

            try(DefaultHttpClient client = client()) {
                MutableHttpRequest<String> request = HttpRequest
                    .POST(apiUri("/flows/validate"), body).contentType(MediaType.APPLICATION_YAML);

                List<ValidateConstraintViolation> validations = client.toBlocking().retrieve(
                    this.requestOptions(request),
                    Argument.listOf(ValidateConstraintViolation.class)
                );

                validations
                    .forEach(throwConsumer(validation -> {
                        if (validation.getConstraints() == null) {
                            stdOut("@|green \u2713|@ - " + validation.getIdentity());
                        } else {
                            stdErr("@|red \u2718|@ - " + validation.getIdentity(directory));
                            AbstractValidateCommand.handleValidateConstraintViolation(validation, clsName);
                            returnCode.set(1);
                        }
                    }));
            } catch (HttpClientResponseException e){
                AbstractValidateCommand.handleHttpException(e, clsName);

                return 1;
            }
        }

        return returnCode.get();
    }
}
