package io.kestra.webserver.exceptions;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;

import javax.inject.Singleton;

@Produces(value = MediaType.TEXT_PLAIN)
@Singleton
@Requires(classes = {IllegalArgumentException.class, ExceptionHandler.class})
@SuppressWarnings("rawtypes")
public class IllegalArgumentExceptionHandler implements ExceptionHandler<IllegalArgumentException, HttpResponse> {
    @Override
    public HttpResponse handle(HttpRequest request, IllegalArgumentException exception) {
        return HttpResponse.unprocessableEntity().body(exception.getMessage());
    }
}
