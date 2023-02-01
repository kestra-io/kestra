package io.kestra.cli;

import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.serializers.YamlFlowParser;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import picocli.CommandLine;

import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;


public class AbstractValidateCommand extends AbstractApiCommand {
    @CommandLine.Option(names = {"--local"}, description = "If validation should be done by the client", defaultValue = "false")
    protected boolean local;

    @CommandLine.Parameters(index = "0", description = "the directory containing flows to check")
    public Path directory;

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
        return String.join("\n---\n", Files.walk(directory)
            .filter(Files::isRegularFile)
            .filter(YamlFlowParser::isValidExtension)
            .map(path -> {
                try {
                    return Files.readString(path, Charset.defaultCharset());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })
            .collect(Collectors.toList()));
    }
}
