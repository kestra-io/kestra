package io.kestra.webserver.controllers;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;

import io.kestra.webserver.exceptions.BulkValidationException;
import io.kestra.core.exceptions.ValidationErrorException;
import io.kestra.webserver.errors.ProblemDetail;
import io.kestra.webserver.errors.ProblemError;
import io.kestra.webserver.errors.ProblemTypes;
import io.kestra.webserver.errors.ProblemFactory;

import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.server.exceptions.NotAllowedException;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;

/**
 * Translates every exception reaching the HTTP layer into an RFC 9457 problem document.
 *
 * <p>Which {@link io.kestra.webserver.errors.ProblemType} an exception maps to is decided by
 * {@link io.kestra.webserver.errors.ProblemMapperRegistry}, not here, so the catch-all below covers the vast
 * majority of cases. The remaining handlers exist only because they can populate the {@code errors} array,
 * which needs knowledge of the specific exception's structure.
 *
 * <p>Micronaut resolves {@code @Error} routes ahead of {@code ExceptionHandler} beans, and the catch-all here
 * always matches — which is why registering an {@code ExceptionHandler} for a server exception has no effect.
 */
@Controller
public class ErrorController {
    private final ProblemFactory problems;

    @Inject
    public ErrorController(final ProblemFactory problems) {
        this.problems = Objects.requireNonNull(problems, "problems must not be null");
    }

    @Error(global = true)
    public HttpResponse<ProblemDetail> error(HttpRequest<?> request, Throwable e) {
        MutableHttpResponse<ProblemDetail> response = this.problems.response(request, e);
        // A 405 must advertise the methods that would have worked (RFC 9110 §15.5.6). Micronaut's own handler
        // did this, but it is shadowed by the catch-all above, so the header is set here instead.
        if (e instanceof NotAllowedException notAllowed && notAllowed.getAllowedMethods() != null) {
            response.header(HttpHeaders.ALLOW, String.join(", ", notAllowed.getAllowedMethods()));
        }
        return response;
    }

    /** Bean validation: one {@code errors} entry per violated constraint. */
    @Error(global = true)
    public HttpResponse<ProblemDetail> error(HttpRequest<?> request, ConstraintViolationException e) {
        return this.problems.response(
            request,
            e,
            ProblemTypes.VALIDATION_FAILED,
            ProblemError.ofViolations(e.getConstraintViolations())
        );
    }

    /** Bulk endpoints: one {@code errors} entry per rejected item. */
    @Error(global = true)
    public HttpResponse<ProblemDetail> error(HttpRequest<?> request, BulkValidationException e) {
        return this.problems.response(request, e, ProblemTypes.BULK_VALIDATION_FAILED, e.errors());
    }

    /** Resource validation, which reports its problems as plain strings with no path. */
    @Error(global = true)
    public HttpResponse<ProblemDetail> error(HttpRequest<?> request, ValidationErrorException e) {
        return this.problems.response(
            request,
            e,
            ProblemTypes.VALIDATION_FAILED,
            e.getInvalids() == null ? List.of() : e.getInvalids().stream().map(ProblemError::of).toList()
        );
    }

    /**
     * Body decoding failed. The underlying Jackson exception knows where in the document the problem is, which
     * is the only reason this is handled separately from the catch-all.
     */
    @Error(global = true)
    public HttpResponse<ProblemDetail> error(HttpRequest<?> request, ConversionErrorException e) {
        Throwable cause = e.getConversionError().getCause();

        if (cause instanceof InvalidTypeIdException invalidTypeId) {
            return this.problems.response(
                request,
                e,
                ProblemTypes.INVALID_PLUGIN_TYPE,
                List.of(ProblemError.of(
                    "Unknown type '%s'.".formatted(invalidTypeId.getTypeId()),
                    null,
                    pathOf(invalidTypeId)
                ))
            );
        }

        if (cause instanceof JsonMappingException mappingException) {
            String path = pathOf(mappingException);
            return this.problems.responseWithoutMessage(
                request,
                e,
                ProblemTypes.INVALID_JSON,
                path.isEmpty() ? List.of() : List.of(ProblemError.of(null, null, path))
            );
        }

        return this.problems.responseWithoutMessage(request, e);
    }

    /** A request that matched no route at all, and so carries no exception. */
    @Error(global = true, status = HttpStatus.NOT_FOUND)
    public HttpResponse<ProblemDetail> notFound(HttpRequest<?> request) {
        return this.problems.response(request, null, ProblemTypes.NOT_FOUND, List.of());
    }

    /**
     * The document path Jackson recorded, e.g. {@code tasks[0].type}. Uses the public reference chain rather
     * than the private field the previous implementation reflected into.
     */
    private static String pathOf(final JsonMappingException e) {
        return e.getPath()
            .stream()
            .map(reference -> reference.getFieldName() != null
                ? reference.getFieldName()
                : "[" + reference.getIndex() + "]")
            .collect(Collectors.joining("."))
            .replace(".[", "[");
    }
}
