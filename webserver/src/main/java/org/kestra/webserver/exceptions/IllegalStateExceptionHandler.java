package org.kestra.webserver.exceptions;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;

import javax.inject.Singleton;

@SuppressWarnings("rawtypes")
@Produces(value = MediaType.TEXT_PLAIN)
@Singleton
@Requires(classes = {IllegalStateException.class, ExceptionHandler.class})
public class IllegalStateExceptionHandler implements ExceptionHandler<IllegalStateException, HttpResponse> {
    @Override
    public HttpResponse handle(HttpRequest request, IllegalStateException exception) {
        return HttpResponse.unprocessableEntity().body(exception.getMessage());
    }
}
