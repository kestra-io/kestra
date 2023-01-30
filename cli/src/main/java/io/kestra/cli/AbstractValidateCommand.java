package io.kestra.cli;

import io.micronaut.http.client.exceptions.HttpClientResponseException;

import javax.validation.ConstraintViolationException;

public class AbstractValidateCommand extends AbstractCommand {

    public static void handleException(ConstraintViolationException e, String resource) {
        stdErr("@|fg(red) Unable to parse {0} due to the following error(s):|@", resource);
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

    public static void handleHttpException(HttpClientResponseException e, String resource) {
        stdErr("@|fg(red) Unable to update {0}s due to the following error:|@", resource);
        stdErr(
            "- @|bold,yellow {0}|@",
            e.getMessage()
        );
    }
}
