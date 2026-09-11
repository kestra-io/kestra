package io.kestra.webserver.filter;

import java.util.List;
import java.util.Objects;

import io.kestra.webserver.errors.ProblemDetail;
import io.kestra.webserver.errors.ProblemFormatExclusion;
import io.kestra.webserver.errors.ProblemFactory;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;

/**
 * Gives a problem document to any failed response that reached the client without one.
 *
 * <p>Some errors never enter error handling at all: a filter that short-circuits the chain with
 * {@code return HttpResponse.unauthorized()} produces a response directly, so neither an {@code @Error} route
 * nor the error-response processor ever sees it. Others come from routes that return a bare
 * {@code HttpResponse.status(...)}, or a plain string, instead of throwing. Without this filter those reach
 * the caller as an empty body or as {@code text/plain} from a JSON API, with nothing to act on.
 *
 * <p>Runs at {@link ServerFilterPhase#FIRST}, i.e. as the outermost filter, so its response processing happens
 * <em>last</em> and therefore also sees responses from filters that short-circuit in an earlier phase. Same
 * reasoning as {@link SecurityHeadersFilter}.
 *
 * <p>It only ever fills in a missing body. A response that already declares a content type other than plain
 * JSON is left untouched, and any path claimed by a {@link ProblemFormatExclusion} — SCIM, the Model Context
 * Protocol — is skipped outright, since some of their routes legitimately return an empty body and a problem
 * document would break their own specification.
 */
@ServerFilter("/**")
public class ProblemResponseFilter implements Ordered {
    private final ProblemFactory problems;
    private final List<ProblemFormatExclusion> exclusions;

    public ProblemResponseFilter(final ProblemFactory problems, final List<ProblemFormatExclusion> exclusions) {
        this.problems = Objects.requireNonNull(problems, "problems must not be null");
        this.exclusions = Objects.requireNonNull(exclusions, "exclusions must not be null");
    }

    @ResponseFilter
    public void fillProblemBody(@NonNull HttpRequest<?> request, @NonNull MutableHttpResponse<?> response) {
        if (400 > response.status().getCode()) {
            return;
        }
        if (HttpMethod.HEAD == request.getMethod() || this.isExcluded(request.getPath())) {
            return;
        }

        MediaType contentType = response.getContentType().orElse(null);
        if (!isReplaceable(contentType)) {
            return;
        }

        String detail = detailOf(response.body(), contentType);
        if (detail == null && response.body() != null) {
            // A typed body the route meant to send: not ours to replace.
            return;
        }

        ProblemDetail problem = this.problems.detailForStatus(
            request,
            null,
            response.status().getCode(),
            detail,
            List.of()
        );
        writeProblem(response, problem);
    }

    /**
     * Only an absent content type, plain JSON, or plain text may be replaced. Anything more specific — including
     * any other {@code +json} variant — belongs to whoever set it.
     */
    private static boolean isReplaceable(@Nullable final MediaType contentType) {
        return contentType == null
            || MediaType.APPLICATION_JSON_TYPE.equals(contentType)
            || MediaType.TEXT_PLAIN_TYPE.equals(contentType);
    }

    /**
     * The text to report, or {@code null} when the body is a typed object that must be left alone. An empty body
     * yields {@code null} too, but is distinguished by the caller.
     */
    @Nullable
    private static String detailOf(@Nullable final Object body, @Nullable final MediaType contentType) {
        if (body == null) {
            return null;
        }
        boolean isPlainText = contentType == null || MediaType.TEXT_PLAIN_TYPE.equals(contentType);
        return body instanceof CharSequence text && isPlainText ? text.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private static void writeProblem(final MutableHttpResponse<?> response, final ProblemDetail problem) {
        ((MutableHttpResponse<Object>) response)
            .contentType(MediaType.APPLICATION_JSON_PROBLEM_TYPE)
            .body(problem);
    }

    private boolean isExcluded(final String path) {
        return this.exclusions.stream().anyMatch(exclusion -> exclusion.excludes(path));
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.FIRST.order();
    }
}
