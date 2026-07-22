package io.kestra.webserver.controllers.api;

import io.kestra.core.exceptions.ResourceAccessDeniedException;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller("/test-utils")
public class TestUtilsController {

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/failing-with-400-client-error", produces = "application/json")
    public String failingWith400ClientError() {
        throw new HttpStatusException(HttpStatus.BAD_REQUEST, "a client error message");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/failing-with-forbidden", produces = "application/json")
    public String failingWithForbidden() {
        throw new ResourceAccessDeniedException("Namespace io.kestra.denied is not allowed.");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/failing-with-500-server-error", produces = "application/json")
    public String failingWith500ServerError() {
        throw new RuntimeException("an unhandled server error message");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/failing-with-server-error-with-no-error-message", produces = "application/json")
    public String failingWithServerErrorWithNoErrorMessage() {
        throw new NullPointerException();
    }
}
