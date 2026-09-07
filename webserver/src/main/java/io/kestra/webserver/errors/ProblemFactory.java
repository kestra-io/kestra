package io.kestra.webserver.errors;

import java.util.List;
import java.util.Objects;

import io.kestra.core.utils.IdUtils;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds every {@link ProblemDetail} the API emits, and the only place the rules that make them safe live:
 * a server error never echoes its exception message, and always carries a {@code traceId} that ties the
 * response to the log entry holding the real cause.
 */
@Slf4j
@Singleton
public class ProblemFactory {
    /**
     * The {@code detail} of every server error. Fixed rather than derived from the exception, so an internal
     * message can never reach the caller; the real message goes to the log under the same {@code traceId}.
     */
    static final String SERVER_ERROR_DETAIL = "An unexpected error occurred. Quote the traceId when contacting support.";

    private final ProblemMapperRegistry registry;

    @Inject
    public ProblemFactory(final ProblemMapperRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    /** Reports a throwable, resolving its type through the registry. */
    public MutableHttpResponse<ProblemDetail> response(final HttpRequest<?> request, final Throwable throwable) {
        return this.response(request, throwable, this.registry.resolve(throwable), List.of());
    }

    /** Reports a throwable as a known type, optionally with field-level errors. */
    public MutableHttpResponse<ProblemDetail> response(
        final HttpRequest<?> request,
        final Throwable throwable,
        final ProblemType type,
        final List<ProblemError> errors
    ) {
        return this.respond(this.build(request, throwable, type, statusOf(throwable, type), messageOf(throwable), errors));
    }

    /**
     * Reports a client error whose exception message must not reach the caller: a Jackson decoding failure
     * names the Java class it could not construct (kestra-ee#10266). The {@code detail} becomes the type's
     * title, and the exception is still logged.
     */
    public MutableHttpResponse<ProblemDetail> responseWithoutMessage(final HttpRequest<?> request, final Throwable throwable) {
        return this.responseWithoutMessage(request, throwable, this.registry.resolve(throwable), List.of());
    }

    /** @see #responseWithoutMessage(HttpRequest, Throwable) */
    public MutableHttpResponse<ProblemDetail> responseWithoutMessage(
        final HttpRequest<?> request,
        final Throwable throwable,
        final ProblemType type,
        final List<ProblemError> errors
    ) {
        return this.respond(this.build(request, throwable, type, statusOf(throwable, type), null, errors));
    }

    private MutableHttpResponse<ProblemDetail> respond(final ProblemDetail problem) {
        return HttpResponse.<ProblemDetail>status(HttpStatus.valueOf(problem.status()))
            .contentType(MediaType.APPLICATION_JSON_PROBLEM_TYPE)
            .body(problem);
    }

    /**
     * Builds the document for a response whose status is already decided — a rejection raised before routing,
     * or a route that returned a bare {@code HttpResponse.status(...)}. The status given here wins over the
     * resolved type's, so an unusual status is reported as sent rather than normalised away.
     *
     * @param detail the text the route supplied, if any; ignored for server errors
     */
    public ProblemDetail detailForStatus(
        final HttpRequest<?> request,
        final Throwable throwable,
        final int status,
        final String detail,
        final List<ProblemError> errors
    ) {
        return this.build(request, throwable, ProblemTypes.byStatus(status), status, detail, errors);
    }

    private ProblemDetail build(
        final HttpRequest<?> request,
        final Throwable throwable,
        final ProblemType type,
        final int status,
        final String detail,
        final List<ProblemError> errors
    ) {
        String instance = request == null ? null : request.getPath();

        if (500 <= status) {
            String traceId = traceId();
            log.error(
                "Problem [traceId={}] {} {} -> {} {}: {}",
                traceId,
                request == null ? "-" : request.getMethodName(),
                instance,
                status,
                type.slug(),
                detail == null ? "" : detail,
                throwable
            );
            return new ProblemDetail(type.uri(), type.title(), status, SERVER_ERROR_DETAIL, instance, List.of(), traceId);
        }

        if (log.isDebugEnabled()) {
            log.debug(
                "Problem {} {} -> {} {}: {}",
                request == null ? "-" : request.getMethodName(),
                instance,
                status,
                type.slug(),
                detail == null ? "" : detail,
                throwable
            );
        }

        // Fall back to the title so detail is never empty: an error the caller cannot read is the defect
        // this whole format exists to fix.
        String safeDetail = detail == null || detail.isBlank() ? type.title() : detail;
        return new ProblemDetail(type.uri(), type.title(), status, safeDetail, instance, errors, null);
    }

    /** An explicitly thrown status wins over the type's documented one. */
    private static int statusOf(final Throwable throwable, final ProblemType type) {
        if (throwable instanceof HttpStatusException e) {
            return e.getStatus().getCode();
        }
        if (throwable instanceof HttpClientResponseException e) {
            return e.getStatus().getCode();
        }
        return type.status();
    }

    private static String messageOf(final Throwable throwable) {
        return throwable == null ? null : throwable.getMessage();
    }

    /**
     * The active OpenTelemetry trace id, or a synthetic one when tracing is disabled — which is the default,
     * and where omitting the field would leave support with nothing to correlate on.
     */
    private static String traceId() {
        SpanContext spanContext = Span.current().getSpanContext();
        return spanContext.isValid() ? spanContext.getTraceId() : IdUtils.create();
    }
}
