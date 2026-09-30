package io.kestra.webserver.errors;

import java.util.List;
import java.util.Objects;


import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.JsonErrorResponseBodyProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Renders problem documents for the few error responses that never reach an {@code @Error} route: a rejection
 * raised before routing, or an error route that itself threw.
 *
 * <p>Declaring this bean displaces Micronaut's {@code DefaultJsonErrorResponseBodyProvider} — which is
 * conditional on no other {@link JsonErrorResponseBodyProvider} existing — and so replaces the HATEOAS
 * {@code JsonError} body it produced. The HTML provider is left alone, so browsers hitting a bad URL still get
 * a page rather than a JSON document.
 */
@Singleton
public class ProblemJsonErrorResponseBodyProvider implements JsonErrorResponseBodyProvider<ProblemDetail> {
    private final ProblemFactory problems;

    @Inject
    public ProblemJsonErrorResponseBodyProvider(final ProblemFactory problems) {
        this.problems = Objects.requireNonNull(problems, "problems must not be null");
    }

    @Override
    public String contentType() {
        return MediaType.APPLICATION_JSON_PROBLEM;
    }

    @Override
    public ProblemDetail body(final ErrorContext context, final HttpResponse<?> response) {
        List<ProblemError> errors = context.getErrors()
            .stream()
            .map(error -> ProblemError.of(error.getMessage(), null, error.getPath().orElse(null)))
            .toList();

        Throwable cause = context.getRootCause().orElse(null);
        return this.problems.detailForStatus(
            context.getRequest(),
            cause,
            response.status().getCode(),
            cause == null ? firstMessage(errors) : cause.getMessage(),
            errors
        );
    }

    private static String firstMessage(final List<ProblemError> errors) {
        return errors.isEmpty() ? null : errors.getFirst().detail();
    }
}
