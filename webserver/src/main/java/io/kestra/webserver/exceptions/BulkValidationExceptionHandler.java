package io.kestra.webserver.exceptions;

import io.kestra.webserver.responses.BulkErrorResponse;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

@Produces(MediaType.APPLICATION_JSON)
@Singleton
@Requires(classes = {BulkValidationException.class, ExceptionHandler.class})
public class BulkValidationExceptionHandler
    implements ExceptionHandler<BulkValidationException, HttpResponse<BulkErrorResponse>> {

    @Override
    public HttpResponse<BulkErrorResponse> handle(HttpRequest<?> request, BulkValidationException exception) {
        return HttpResponse.badRequest(exception.getBulkErrorResponse());
    }
}
