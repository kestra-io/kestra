package io.kestra.cli;

import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import picocli.CommandLine;

import javax.validation.ConstraintViolationException;


public class AbstractValidateCommand extends AbstractApiCommand {
    @CommandLine.Option(names = {"--local"}, description = "If validation should be done by the client", defaultValue = "false")
    protected boolean local;

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
}
