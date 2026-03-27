package io.kestra.webserver.controllers.api;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@Controller("/test-utils")
public class TestUtilsController {

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/failing-with-400-client-error", produces = "application/json")
    public String failingWith400ClientError() {
        if (true) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "a client error message");
        }
        return "";
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/failing-with-500-server-error", produces = "application/json")
    public String failingWith500ServerError() {
        if (true) {
            throw new RuntimeException("an unhandled server error message");
        }
        return "";
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/failing-with-server-error-with-no-error-message", produces = "application/json")
    public String failingWithServerErrorWithNoErrorMessage() {
        if (true) {
            throw new NullPointerException();
        }
        return "";
    }
}
