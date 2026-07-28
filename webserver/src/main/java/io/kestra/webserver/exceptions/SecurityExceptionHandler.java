package io.kestra.webserver.exceptions;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

/**
 * Maps a {@link SecurityException} to a clean {@code 403 FORBIDDEN} instead of letting it surface as a
 * {@code 500}. This covers authorization denials such as a disabled local-file preview or a path that is
 * not in the {@code kestra.local-files.allowed-paths} allow-list.
 */
@Produces(value = MediaType.TEXT_PLAIN)
@Singleton
@Requires(classes = { SecurityException.class, ExceptionHandler.class })
@SuppressWarnings("rawtypes")
public class SecurityExceptionHandler implements ExceptionHandler<SecurityException, HttpResponse> {
    @Override
    public HttpResponse handle(HttpRequest request, SecurityException exception) {
        return HttpResponse.status(HttpStatus.FORBIDDEN).body(exception.getMessage());
    }
}
